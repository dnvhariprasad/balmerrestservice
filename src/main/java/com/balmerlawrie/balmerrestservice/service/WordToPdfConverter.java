package com.balmerlawrie.balmerrestservice.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.Docx4J;

public class WordToPdfConverter {

    public static byte[] convertWordToPdf(byte[] wordBytes) throws Exception {
        try (
                ByteArrayInputStream wordInputStream = new ByteArrayInputStream(wordBytes);
                ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream()
        ) {
            // Load DOCX into WordprocessingMLPackage
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(wordInputStream);

            // Convert DOCX → PDF (new recommended API)
            Docx4J.toPDF(wordMLPackage, pdfOutputStream);

            return pdfOutputStream.toByteArray();
        } catch (Docx4JException e) {
            throw new RuntimeException("Error converting Word to PDF: " + e.getMessage(), e);
        }
    }
}
