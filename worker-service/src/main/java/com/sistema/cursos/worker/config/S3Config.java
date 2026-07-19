package com.sistema.cursos.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Configuración del cliente AWS S3 para el Worker Service.
 *
 * <p>Construye el {@link S3Client} del AWS SDK v2 usando
 * {@link DefaultCredentialsProvider}, que resuelve las credenciales
 * en el siguiente orden de precedencia (cadena de proveedores):
 * <ol>
 *   <li>Variables de entorno: {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY},
 *       {@code AWS_SESSION_TOKEN} (provistas automáticamente por AWS Academy).</li>
 *   <li>Propiedades del sistema Java ({@code aws.accessKeyId}, etc.).</li>
 *   <li>Perfil de credenciales en {@code ~/.aws/credentials}.</li>
 *   <li>Rol IAM del contenedor/instancia EC2 (Instance Metadata Service).</li>
 * </ol>
 *
 * <p>Las credenciales NUNCA se hardcodean en el código fuente.
 * Para desarrollo local con AWS Academy, se deben exportar las variables
 * de entorno antes de iniciar el Worker Service:
 * <pre>{@code
 * export AWS_ACCESS_KEY_ID=ASIA...
 * export AWS_SECRET_ACCESS_KEY=...
 * export AWS_SESSION_TOKEN=...
 * export AWS_S3_REGION=us-east-1
 * export AWS_S3_BUCKET_NAME=mi-bucket
 * }</pre>
 */
@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    /**
     * Región AWS donde está alojado el bucket S3.
     * Leída de la propiedad {@code aws.s3.region} en {@code application.yml},
     * que a su vez toma el valor de la variable de entorno {@code AWS_S3_REGION}.
     */
    @Value("${aws.s3.region}")
    private String region;

    /**
     * Construye y registra el bean {@link S3Client}.
     *
     * <p>El cliente es thread-safe y puede ser reutilizado por múltiples
     * hilos concurrentes del listener, por lo que se define como singleton
     * (comportamiento por defecto de {@code @Bean}).
     *
     * @return cliente S3 configurado con credenciales del entorno y región especificada
     */
    @Bean
    public S3Client s3Client() {
        log.info("Inicializando S3Client para la región [{}] " +
                 "con DefaultCredentialsProvider (variables de entorno AWS Academy)",
            region);

        S3Client client = S3Client.builder()
            .region(Region.of(region))
            // DefaultCredentialsProvider lee las variables de entorno de AWS Academy
            // en tiempo de ejecución, sin exponer credenciales en el código fuente
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

        log.info("S3Client inicializado correctamente para región [{}]", region);
        return client;
    }
}
