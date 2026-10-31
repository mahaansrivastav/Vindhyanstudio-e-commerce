package com.vindhyanstudio.admin;

import org.apache.poi.ss.usermodel.*;

/** Shared cell styles for the Excel files the owner downloads. */
final class ReportStyles {

    private ReportStyles() {}

    static CellStyle header(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    static CellStyle money(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("₹#,##0.00"));
        return s;
    }

    static CellStyle percent(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("0.0%"));
        return s;
    }

    static CellStyle title(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    static CellStyle bold(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }
}
