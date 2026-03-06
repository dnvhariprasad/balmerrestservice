package com.balmerlawrie.balmerrestservice.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.aspose.words.Document;
import com.aspose.words.SaveFormat;

public class WordToPdfConverter {

    public static byte[] convertWordToPdf(byte[] wordBytes) throws Exception {
        try (
                ByteArrayInputStream wordInputStream = new ByteArrayInputStream(wordBytes);
                ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream()
        ) {
            Document doc = new Document(wordInputStream);
            doc.save(pdfOutputStream, SaveFormat.PDF);
            return pdfOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error converting Word to PDF: " + e.getMessage(), e);
        }
    }
}
