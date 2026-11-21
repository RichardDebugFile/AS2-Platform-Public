package edi.as2adapter.as2;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MdnWriter {

    private static final String CONTENT_TYPE = "Content-Type";

    private MdnWriter() {
    }

    public record Report(String as2Id, String originalId, String disposition, String mic, String algorithm) {}

    public record Body(String contentType, byte[] content) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Body(var otherType, var otherContent)
                    && Objects.equals(contentType, otherType) && Arrays.equals(content, otherContent);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(contentType) + Arrays.hashCode(content);
        }

        @Override
        public String toString() {
            return "Body[contentType=" + contentType + ", bytes=" + content.length + "]";
        }
    }

    public static Body write(Report report, As2Identity signer) {
        String boundary = Mime.boundary();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Reporting-UA", "Plataforma AS2");
        fields.put("Original-Recipient", "rfc822; " + report.as2Id());
        fields.put("Final-Recipient", "rfc822; " + report.as2Id());
        fields.put("Original-Message-ID", report.originalId());
        fields.put("Disposition", "automatic-action/MDN-sent-automatically; " + report.disposition());
        if (report.mic() != null) fields.put("Received-Content-MIC", report.mic() + ", " + report.algorithm());

        byte[] description = Mime.part(Map.of(CONTENT_TYPE, "text/plain; charset=us-ascii"),
                Mime.ascii("AS2 receipt: " + report.disposition()));
        byte[] notification = Mime.part(Map.of(CONTENT_TYPE, "message/disposition-notification"),
                Mime.part(fields, new byte[0]));
        String reportType = "multipart/report; report-type=disposition-notification; boundary=\"" + boundary + "\"";
        byte[] content = Mime.multipart(boundary, List.of(description, notification));
        if (signer == null) return new Body(reportType, content);

        byte[] entity = MessageBuilder.signedEntity(Mime.part(Map.of(CONTENT_TYPE, reportType), content),
                signer, MessageBuilder.MIC_ALGORITHM);
        Mime.Headers headers = Mime.headers(entity);
        return new Body(headers.get("content-type"), headers.body(entity));
    }
}
