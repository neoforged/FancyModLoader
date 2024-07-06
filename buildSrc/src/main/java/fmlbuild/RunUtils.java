package fmlbuild;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

final class RunUtils {
    private RunUtils() {
    }

    static List<String> splitJvmArgs(String jvmArgs) throws IOException {
        StreamTokenizer tok = new StreamTokenizer(new StringReader(jvmArgs));
        tok.resetSyntax();
        tok.wordChars(32, 255);
        tok.whitespaceChars(0, 32);
        tok.quoteChar('"');
        tok.quoteChar('\'');
        tok.commentChar('#');

        var args = new ArrayList<String>();
        while (tok.nextToken() != -1) {
            args.add(tok.sval);
        }
        return args;
    }

    /**
     * We remove any classpath or module-path arguments since both have to be set up with project artifacts,
     * and not artifacts from the installation.
     */
    static void cleanJvmArgs(List<String> jvmArgs) {
        for (int i = 0; i < jvmArgs.size(); i++) {
            var jvmArg = jvmArgs.get(i);
            // Remove the classpath argument
            if ("-cp".equals(jvmArg) || "-classpath".equals(jvmArg)) {
                if (i + 1 < jvmArgs.size() && jvmArgs.get(i + 1).equals("${classpath}")) {
                    jvmArgs.remove(i + 1);
                }
                jvmArgs.remove(i--);
            } else if ("-p".equals(jvmArg) || "--module-path".equals(jvmArg)) {
                if (i + 1 < jvmArgs.size()) {
                    jvmArgs.remove(i + 1);
                }
                jvmArgs.remove(i--);
            }
        }
    }

    static void escapeJvmArgs(List<String> jvmArgs) {
        jvmArgs.replaceAll(RunUtils::escapeJvmArg);
    }

    static String escapeJvmArg(String arg) {
        var escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"");
        if (escaped.contains(" ")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

}
