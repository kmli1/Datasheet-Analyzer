package com.example.datasheetanalyzer.sandbox;
oneway interface IPdfSandboxCallback {
    void completed(boolean passed, String code, int pages, int processId);
}
