package org.idempiere.cli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.GeneratedCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class AiResponseParserService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern FENCE_PATTERN = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?\\})\\s*```", Pattern.DOTALL);

    public record ParseResult(GeneratedCode code, String errorMessage) {
        static ParseResult success(GeneratedCode code) {
            return new ParseResult(code, null);
        }

        static ParseResult failure(String errorMessage) {
            return new ParseResult(null, errorMessage);
        }
    }

    public ParseResult parseDetailed(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParseResult.failure("AI response is empty");
        }

        ParseResult result = tryParseJson(raw.strip(), "raw response");
        if (result.code() != null) {
            return result;
        }
        String lastError = result.errorMessage();

        Matcher fenceMatcher = FENCE_PATTERN.matcher(raw);
        if (fenceMatcher.find()) {
            result = tryParseJson(fenceMatcher.group(1).strip(), "markdown code fence");
            if (result.code() != null) {
                return result;
            }
            lastError = result.errorMessage();
        }

        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            result = tryParseJson(raw.substring(start, end + 1).strip(), "outer JSON block");
            if (result.code() != null) {
                return result;
            }
            lastError = result.errorMessage();
        }

        return ParseResult.failure(lastError != null ? lastError : "No parseable JSON object found in AI response");
    }

    public GeneratedCode parse(String raw) {
        return parseDetailed(raw).code();
    }

    private ParseResult tryParseJson(String json, String source) {
        try {
            GeneratedCode code = OBJECT_MAPPER.readValue(json, GeneratedCode.class);
            if (code != null && code.getFiles() != null && !code.getFiles().isEmpty()) {
                return ParseResult.success(code);
            }
            return ParseResult.failure("Parsed " + source + " but files array is empty");
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            message = message.replace('\n', ' ').replace('\r', ' ');
            return ParseResult.failure("Invalid JSON in " + source + ": " + message);
        }
    }
}
