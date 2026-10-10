package com.example.datasheetanalyzer.sandbox;
oneway interface IPdfFigureCallback {
    void match(int requestIndex, int pageNumber, String title, float left, float top, float right, float bottom);
    void completed(boolean success, String code, int processId);
}
