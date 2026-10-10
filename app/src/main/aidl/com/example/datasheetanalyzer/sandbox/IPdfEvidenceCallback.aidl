package com.example.datasheetanalyzer.sandbox;
oneway interface IPdfEvidenceCallback {
    void rectangle(float left, float top, float right, float bottom);
    void completed(boolean success, String code, int processId);
}
