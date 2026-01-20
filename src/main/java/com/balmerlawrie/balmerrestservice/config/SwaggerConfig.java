package com.balmerlawrie.balmerrestservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

        private static final String SECURITY_SCHEME_NAME = "sessionId";

        @Bean
        public OpenAPI balmerRestServiceOpenAPI() {
                // Server configurations
                Server localServer = new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server");

                Server productionServer = new Server()
                                .url("http://74.225.130.20:8084")
                                .description("Production Server");

                // Contact information
                Contact contact = new Contact()
                                .name("Balmer Lawrie IT Team")
                                .email("support@balmerlawrie.com")
                                .url("https://www.balmerlawrie.com");

                // License
                License license = new License()
                                .name("Proprietary")
                                .url("https://www.balmerlawrie.com");

                // API Info
                Info info = new Info()
                                .title("Balmer REST Service API")
                                .version("1.0.0")
                                .description(
                                                "REST API for Balmer Lawrie DTZ Portal.\n\n" +
                                                                "## Features\n" +
                                                                "- **Authentication**: Login/Logout via iBPS WMConnect\n"
                                                                +
                                                                "- **Session Management**: Cached sessions with 25-minute timeout\n"
                                                                +
                                                                "- **Queue Management**: My Queue, Common Queue, and work item operations\n\n"
                                                                +
                                                                "## Authentication Flow\n" +
                                                                "1. Call `/login-wmConnectCabinet` or `/session` to get a sessionId\n"
                                                                +
                                                                "2. Include `sessionId` header in all subsequent requests\n"
                                                                +
                                                                "3. Call `/disconnect` to logout when done")
                                .contact(contact)
                                .license(license);

                // External documentation
                ExternalDocumentation externalDocs = new ExternalDocumentation()
                                .description("API Documentation HTML")
                                .url("/documentation/endpoints.html");

                // Security scheme for sessionId header
                SecurityScheme sessionIdScheme = new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("sessionId")
                                .description("Session ID obtained from login endpoint");

                Components components = new Components()
                                .addSecuritySchemes(SECURITY_SCHEME_NAME, sessionIdScheme);

                // Tags for organizing endpoints
                List<Tag> tags = List.of(
                                new Tag().name("Authentication").description("User login and logout operations"),
                                new Tag().name("Session Management").description("Cached session management for iBPS"),
                                new Tag().name("Queue Management")
                                                .description("Fetch work items from My Queue and Common Queue"),
                                new Tag().name("Supporting Documents")
                                                .description("Operations for managing supporting documents attached to work items"));

                return new OpenAPI()
                                .info(info)
                                .externalDocs(externalDocs)
                                .servers(List.of(localServer, productionServer))
                                .components(components)
                                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                                .tags(tags);
        }
}
