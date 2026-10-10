package com.example.datasheetanalyzer.sandbox;
import android.os.ParcelFileDescriptor;
import com.example.datasheetanalyzer.sandbox.IPdfSandboxCallback;
import com.example.datasheetanalyzer.sandbox.IPdfTextCallback;
import com.example.datasheetanalyzer.sandbox.IPdfRenderCallback;
import com.example.datasheetanalyzer.sandbox.IPdfFigureCallback;
import com.example.datasheetanalyzer.sandbox.IPdfEvidenceCallback;
oneway interface IPdfSandbox {
    void inspect(in ParcelFileDescriptor descriptor, IPdfSandboxCallback callback);
    void extract(in ParcelFileDescriptor descriptor, IPdfTextCallback callback);
    void render(in ParcelFileDescriptor descriptor, int pageNumber, in ParcelFileDescriptor output, IPdfRenderCallback callback);
    void renderImage(in ParcelFileDescriptor descriptor, in ParcelFileDescriptor output, IPdfRenderCallback callback);
    void findFigures(in ParcelFileDescriptor descriptor, in String[] titles, IPdfFigureCallback callback);
    void findEvidence(in ParcelFileDescriptor descriptor, int pageNumber, String evidence, String section, IPdfEvidenceCallback callback);
    void cancel();
}
