package fr.uge.gitclout.analyze.language;

public enum Regex {

    TYPE_ONE_COMMENT("//.*", "/*", "*/"),
    TYPE_TWO_COMMENT("#.*", "\"\"\"", "\"\"\""),
    TYPE_THREE_COMMENT("#.*", "=", "=");


    private final String regex;
    private final String regexStart;
    private final String regexEnd;

    Regex(String regex, String regexStart, String regexEnd) {
        this.regex = regex;
        this.regexStart = regexStart;
        this.regexEnd = regexEnd;
    }

    public String getRegex() {
        return regex;
    }

    public String getRegexStart() {
        return regexStart;
    }

    public String getRegexEnd() {
        return regexEnd;
    }
}
