package com.balmerlawrie.balmerrestservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Configuration
public class AsposeLicenseConfig {

    private static final Logger log = LoggerFactory.getLogger(AsposeLicenseConfig.class);

    @PostConstruct
    public void applyAsposeLicense() {
        try (InputStream licenseStream = getClass().getResourceAsStream("/Aspose.Total.Java.lic")) {
            if (licenseStream == null) {
                log.warn("Aspose license file not found in classpath. Running in evaluation mode.");
                return;
            }
            com.aspose.pdf.License pdfLicense = new com.aspose.pdf.License();
            pdfLicense.setLicense(licenseStream);
            log.info("Aspose.PDF license applied successfully.");
        } catch (Exception e) {
            log.error("Failed to apply Aspose license: {}", e.getMessage(), e);
        }
    }
}
