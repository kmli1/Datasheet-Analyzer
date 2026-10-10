package com.example.datasheetanalyzer.sandbox;
oneway interface IPdfTextCallback {
    void metadata(String title, String author, int totalPages);
    void page(int pageNumber, String text, boolean truncated);
    void completed(boolean success, String code, String detail, boolean truncated, int processId);
}
