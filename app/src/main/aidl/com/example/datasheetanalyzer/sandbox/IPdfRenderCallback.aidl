package com.example.datasheetanalyzer.sandbox;
oneway interface IPdfRenderCallback {
    void completed(boolean success, String code, int width, int height, int processId);
}
