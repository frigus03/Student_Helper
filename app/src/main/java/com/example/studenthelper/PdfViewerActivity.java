package com.example.studenthelper;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfViewerActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE = "pdf_source";
    public static final String EXTRA_TITLE = "pdf_title";

    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();
    private RecyclerView pagesView;
    private View loadingView;
    private View errorView;
    private TextView errorText;
    private PdfRenderer renderer;
    private ParcelFileDescriptor fileDescriptor;
    private String source;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        source = getIntent().getStringExtra(EXTRA_SOURCE);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        TextView titleView = findViewById(R.id.tvPdfTitle);
        titleView.setText(title == null || title.trim().isEmpty() ? "PDF" : title);
        pagesView = findViewById(R.id.rvPdfPages);
        loadingView = findViewById(R.id.layoutPdfLoading);
        errorView = findViewById(R.id.layoutPdfError);
        errorText = findViewById(R.id.tvPdfError);

        findViewById(R.id.btnPdfBack).setOnClickListener(v -> finish());
        MaterialButton retry = findViewById(R.id.btnPdfRetry);
        retry.setOnClickListener(v -> loadPdf());
        pagesView.setLayoutManager(new LinearLayoutManager(this));
        loadPdf();
    }

    private void loadPdf() {
        if (source == null || source.trim().isEmpty()) {
            showError("This note doesn't have a PDF file attached.");
            return;
        }
        loadingView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        pagesView.setVisibility(View.GONE);

        pdfExecutor.execute(() -> {
            try {
                File pdfFile = resolvePdfFile(source);
                ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer pdfRenderer = new PdfRenderer(descriptor);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        pdfRenderer.close();
                        try { descriptor.close(); } catch (Exception ignored) { }
                        return;
                    }
                    fileDescriptor = descriptor;
                    renderer = pdfRenderer;
                    pagesView.setAdapter(new PdfPageAdapter());
                    loadingView.setVisibility(View.GONE);
                    pagesView.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("We couldn't open this PDF. Check that the backend is running and try again."));
            }
        });
    }

    private File resolvePdfFile(String pathOrUrl) throws Exception {
        if (!pathOrUrl.startsWith("http://") && !pathOrUrl.startsWith("https://")) {
            File localFile = new File(pathOrUrl);
            if (!localFile.isFile()) throw new IllegalStateException("PDF file is missing");
            return localFile;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(pathOrUrl).openConnection();
        connection.setRequestProperty(AppConfig.APP_TOKEN_HEADER, AppConfig.APP_TOKEN_VALUE);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("PDF download failed: " + status);
            File output = new File(getCacheDir(), "study_material_" + Math.abs(pathOrUrl.hashCode()) + ".pdf");
            try (InputStream input = connection.getInputStream(); FileOutputStream stream = new FileOutputStream(output)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) stream.write(buffer, 0, read);
            }
            return output;
        } finally {
            connection.disconnect();
        }
    }

    private void showError(String message) {
        if (isFinishing() || isDestroyed()) return;
        loadingView.setVisibility(View.GONE);
        pagesView.setVisibility(View.GONE);
        errorText.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private final class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageHolder> {
        @Override
        public PageHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            ImageView image = new ImageView(parent.getContext());
            image.setAdjustViewBounds(true);
            image.setBackgroundColor(Color.WHITE);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setElevation(dp(2));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(12);
            image.setLayoutParams(params);
            return new PageHolder(image);
        }

        @Override
        public void onBindViewHolder(PageHolder holder, int position) {
            holder.image.setTag(position);
            holder.image.setImageDrawable(null);
            holder.image.setContentDescription("PDF page " + (position + 1));
            pdfExecutor.execute(() -> {
                Bitmap bitmap = null;
                try (PdfRenderer.Page page = renderer.openPage(position)) {
                    int availableWidth = Math.max(dp(320), pagesView.getWidth() - dp(24));
                    float scale = Math.min(availableWidth / (float) page.getWidth(), 2f);
                    int width = Math.max(1, (int) (page.getWidth() * scale));
                    int height = Math.max(1, (int) (page.getHeight() * scale));
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                } catch (Exception ignored) { }
                Bitmap rendered = bitmap;
                if (rendered != null) runOnUiThread(() -> {
                    if (!isFinishing() && holder.image.getTag() instanceof Integer
                            && ((Integer) holder.image.getTag()) == position) {
                        holder.image.setImageBitmap(rendered);
                    } else {
                        rendered.recycle();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return renderer == null ? 0 : renderer.getPageCount();
        }

        final class PageHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            PageHolder(ImageView image) { super(image); this.image = image; }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pdfExecutor.execute(() -> {
            if (renderer != null) renderer.close();
            if (fileDescriptor != null) {
                try { fileDescriptor.close(); } catch (Exception ignored) { }
            }
        });
        pdfExecutor.shutdown();
    }
}
