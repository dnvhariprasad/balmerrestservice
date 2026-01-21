package com.balmerlawrie.balmerrestservice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone tool to analyze PDF coordinates and verify View text positions.
 * Run with: mvn exec:java -Dexec.mainClass="com.balmerlawrie.balmerrestservice.PdfCoordinateAnalyzer"
 */
public class PdfCoordinateAnalyzer {

    public static void main(String[] args) throws Exception {
        String pdfPath = "./tmp/notesheets/newNoteContent-703cc202-9652-45ec-a374-e26cbdd3d67e.pdf";

        if (args.length > 0) {
            pdfPath = args[0];
        }

        System.out.println("=".repeat(70));
        System.out.println("PDF COORDINATE ANALYZER");
        System.out.println("=".repeat(70));
        System.out.println("PDF: " + pdfPath);
        System.out.println();

        analyzePdf(pdfPath);
    }

    public static void analyzePdf(String pdfPath) throws Exception {
        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            System.err.println("PDF file not found: " + pdfPath);
            return;
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            // Get page info
            PDPage page = document.getPage(0);
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            System.out.println("PAGE INFO:");
            System.out.println("  Width: " + pageWidth + " points");
            System.out.println("  Height: " + pageHeight + " points");
            System.out.println();

            // Extract all text with positions
            ViewTextFinder finder = new ViewTextFinder();
            finder.setSortByPosition(true);
            finder.setStartPage(1);
            finder.setEndPage(document.getNumberOfPages());
            finder.getText(document);

            List<ViewPosition> positions = finder.getViewPositions();

            System.out.println("VIEW TEXT POSITIONS (PDFBox coordinates):");
            System.out.println("-".repeat(70));

            int viewIndex = 0;
            for (ViewPosition pos : positions) {
                System.out.printf("View #%d: x=%.1f, y=%.1f, width=%.1f, height=%.1f%n",
                    viewIndex, pos.x, pos.y, pos.width, pos.height);

                // Calculate OmniDocs coordinates using different methods
                System.out.println("  Coordinate conversions:");

                // Method 1: Scale factor (current implementation)
                double X_SCALE = 675.0 / 513.0;
                int FIRST_ROW_Y = 330;
                int ROW_HEIGHT = 20;
                int x1_method1 = (int) Math.round(pos.x * X_SCALE);
                int y1_method1 = FIRST_ROW_Y + (viewIndex * ROW_HEIGHT);
                System.out.printf("    Method 1 (Fixed Y + rowIndex): x=%d, y=%d%n", x1_method1, y1_method1);

                // Method 2: Scale both X and Y
                double Y_SCALE = 334.0 / 262.0;
                int x1_method2 = (int) Math.round(pos.x * X_SCALE);
                int y1_method2 = (int) Math.round(pos.y * Y_SCALE);
                System.out.printf("    Method 2 (Scale X and Y): x=%d, y=%d%n", x1_method2, y1_method2);

                // Method 3: Fixed offset
                int X_OFFSET = 162;
                int Y_OFFSET = 72;
                int x1_method3 = (int) pos.x + X_OFFSET;
                int y1_method3 = (int) pos.y + Y_OFFSET;
                System.out.printf("    Method 3 (Fixed offset): x=%d, y=%d%n", x1_method3, y1_method3);

                // Method 4: Use PDFBox Y directly with scale
                int x1_method4 = (int) Math.round(pos.x * X_SCALE);
                int y1_method4 = (int) Math.round(pos.y * X_SCALE); // Same scale for both
                System.out.printf("    Method 4 (Same scale X/Y): x=%d, y=%d%n", x1_method4, y1_method4);

                System.out.println();
                viewIndex++;
            }

            // Calculate row spacing from PDFBox data
            if (positions.size() >= 2) {
                float deltaY = positions.get(1).y - positions.get(0).y;
                System.out.println("ROW SPACING ANALYSIS:");
                System.out.println("  PDFBox row spacing (Y2-Y1): " + deltaY + " points");
                System.out.println("  Scaled spacing (* 1.316): " + (deltaY * 1.316));
                System.out.println("  Scaled spacing (* 1.275): " + (deltaY * 1.275));
            }

            System.out.println();
            System.out.println("CALIBRATION DATA (from user's manual annotation):");
            System.out.println("  User's TT annotation on first View: X1=675, Y1=334");
            System.out.println("  PDFBox expected first View: x=513, y=262");
            System.out.println();

            if (!positions.isEmpty()) {
                ViewPosition first = positions.get(0);
                System.out.println("ACTUAL PDFBox first View position:");
                System.out.printf("  x=%.1f, y=%.1f%n", first.x, first.y);
                System.out.println();

                // Calculate correct scale based on actual data
                double actualXScale = 675.0 / first.x;
                double actualYScale = 334.0 / first.y;
                System.out.println("CORRECTED SCALE FACTORS (based on actual PDFBox data):");
                System.out.printf("  X_SCALE = 675 / %.1f = %.4f%n", first.x, actualXScale);
                System.out.printf("  Y_SCALE = 334 / %.1f = %.4f%n", first.y, actualYScale);

                // Recalculate with corrected scales
                System.out.println();
                System.out.println("RECALCULATED POSITIONS (with corrected scales):");
                viewIndex = 0;
                for (ViewPosition pos : positions) {
                    int x = (int) Math.round(pos.x * actualXScale);
                    int y = (int) Math.round(pos.y * actualYScale);
                    System.out.printf("  View #%d: OmniDocs x=%d, y=%d%n", viewIndex, x, y);
                    viewIndex++;
                }
            }
        }
    }

    static class ViewTextFinder extends PDFTextStripper {
        private List<ViewPosition> viewPositions = new ArrayList<>();
        private boolean foundFirstView = false;

        public ViewTextFinder() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (text.contains("View")) {
                int idx = text.indexOf("View");
                if (idx >= 0 && idx < textPositions.size()) {
                    TextPosition startPos = textPositions.get(idx);
                    int endIdx = Math.min(idx + 3, textPositions.size() - 1);
                    TextPosition endPos = textPositions.get(endIdx);

                    float x = startPos.getXDirAdj();
                    float y = startPos.getYDirAdj();
                    float width = endPos.getXDirAdj() + endPos.getWidthDirAdj() - x;
                    float height = startPos.getHeightDir();

                    // Filter by x-position (View column is on right side)
                    if (x > 450) {
                        if (!foundFirstView) {
                            // Skip header row
                            foundFirstView = true;
                            System.out.println("Skipping header 'View' at x=" + x + ", y=" + y);
                        } else {
                            viewPositions.add(new ViewPosition(x, y, width, height));
                        }
                    }
                }
            }
            super.writeString(text, textPositions);
        }

        public List<ViewPosition> getViewPositions() {
            return viewPositions;
        }
    }

    static class ViewPosition {
        float x, y, width, height;

        ViewPosition(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
