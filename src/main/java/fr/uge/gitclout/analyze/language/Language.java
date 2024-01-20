package fr.uge.gitclout.analyze.language;

public enum Language {

    // Language
    JAVA("java", "java", Regex.TYPE_ONE_COMMENT, "#caf270"),
    PY( "py", "python", Regex.TYPE_TWO_COMMENT, "#ff0000"),
    C("c", "c", Regex.TYPE_ONE_COMMENT, "#555555"),
    JS("js", "javascript", Regex.TYPE_ONE_COMMENT, "#f1e05a"),
    RUBY("rb", "ruby", Regex.TYPE_THREE_COMMENT, "#701516"),
    PHP("php", "php", Regex.TYPE_ONE_COMMENT, "#4F5D95"),
    CSS("css", "css", Regex.TYPE_ONE_COMMENT, "#563d7c"),
    TS("ts", "typescript", Regex.TYPE_ONE_COMMENT, "#2b7489"),
    CPP("cpp", "cpp", Regex.TYPE_ONE_COMMENT, "#FDCBB8"),

    XML("xml", "pom.xml", "#0060a3"),
    GITIGNORE("gitignore", ".gitignore", "#45c490"),

    // IMG
    JPG("jpg", "jpg", "#f34b7d"),
    PNG("png", "png", "#008d93"),
    GIF("gif", "gif", "#2980b9"),
    SVG("svg", "svg", "#c0392b"),

    MD("md", "readme", "#1abc9c"),
    TXT("txt", "txt", "#d35400"),
    PROPERTIES("properties", "properties", "#8e44ad"),

    COMMENTS("comments", "comments", "#555555");


    private final String name;
    private final String displayName;
    private final Regex regex;

    private final String color;

    Language(String name, String displayName, Regex regex, String color) {
        this.name = name;
        this.displayName = displayName;
        this.regex = regex;
        this.color = color;
    };

    Language(String name, String displayName, String color) {
        this.name = name;
        this.displayName = displayName;
        this.regex = null;
        this.color = color;
    };
    public boolean isImage() {
        return this == JPG || this == PNG || this == GIF || this == SVG;
    }

    public String getName() {
        return name;
    }

    public Regex getRegex() {
        return regex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }
}
