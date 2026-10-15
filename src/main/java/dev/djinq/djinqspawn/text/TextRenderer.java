package dev.djinq.djinqspawn.text;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextRenderer {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern EXPANDED_HEX = Pattern.compile(
            "(?i)(?:&x(?:&[0-9a-f]){6}|\\u00A7x(?:\\u00A7[0-9a-f]){6})");
    private static final Pattern COMPACT_HEX = Pattern.compile("(?i)[&\\u00A7]#([0-9a-f]{6})");
    private static final Pattern LEGACY = Pattern.compile("(?i)[&\\u00A7]([0-9a-fk-or])");

    private TextRenderer() {
    }

    public static Component render(String template, Map<String, String> values) {
        String rendered = normalize(template);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "%" + entry.getKey() + "%";
            rendered = rendered.replace(placeholder, MINI_MESSAGE.escapeTags(entry.getValue()));
        }
        return MINI_MESSAGE.deserialize(rendered);
    }

    public static Component render(String template) {
        return render(template, Map.of());
    }

    static String normalize(String input) {
        String text = input == null ? "" : input;
        text = replace(EXPANDED_HEX, text, match -> expandedHexTag(match.group()));
        text = replace(COMPACT_HEX, text, match -> "<reset><color:#" + match.group(1) + ">");
        return replace(LEGACY, text, match -> legacyTag(match.group(1).charAt(0)));
    }

    private static String expandedHexTag(String value) {
        StringBuilder hex = new StringBuilder(6);
        for (int index = 2; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.digit(character, 16) >= 0) {
                hex.append(character);
            }
        }
        return "<reset><color:#" + hex + ">";
    }

    private static String replace(Pattern pattern, String input, Function<Matcher, String> replacement) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        StringBuffer result = new StringBuffer(input.length());
        do {
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.apply(matcher)));
        } while (matcher.find());
        matcher.appendTail(result);
        return result.toString();
    }

    private static String legacyTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }
}

