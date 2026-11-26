package org.example.identidade;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/** Certificado e chave efêmeros, confiados somente pelo cliente da fixture. */
public final class HttpsTestFixture {
    private final SSLContext tls;
    private final Map<String, Object> properties;

    public HttpsTestFixture(Path temporary) throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        var subject = new X500Name("CN=localhost");
        var certificate = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(60)), Date.from(Instant.now().plusSeconds(3600)), subject, pair.getPublic());
        certificate.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName[]{new GeneralName(GeneralName.dNSName, "localhost"),
                        new GeneralName(GeneralName.iPAddress, "127.0.0.1")}));
        var signed = new JcaX509CertificateConverter().getCertificate(certificate.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        char[] password = UUID.randomUUID().toString().toCharArray();
        var store = KeyStore.getInstance("PKCS12");
        store.load(null, password);
        store.setKeyEntry("test-only", pair.getPrivate(), password, new java.security.cert.Certificate[]{signed});
        Path file = temporary.resolve("session-test.p12");
        try (var output = Files.newOutputStream(file)) { store.store(output, password); }
        var trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("test-only", signed);
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        tls = SSLContext.getInstance("TLS");
        tls.init(null, managers.getTrustManagers(), null);
        properties = Map.of("server.address", "127.0.0.1", "server.port", "0", "server.ssl.enabled", "true",
                "server.ssl.key-store", file.toUri().toString(), "server.ssl.key-store-password", new String(password),
                "server.ssl.key-store-type", "PKCS12", "server.ssl.key-alias", "test-only",
                "logging.level.root", "ERROR", "spring.main.banner-mode", "off");
    }

    public SSLContext tls() { return tls; }

    public ServletWebServerApplicationContext start(String[] profiles, Class<?>... sources) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(sources).profiles(profiles)
                .properties(properties).run("--server.port=0", "--server.address=127.0.0.1");
    }
}
