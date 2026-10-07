#!/usr/bin/env python3
"""Applies the minimal `eg` command integration onto a checkout of upstream termux-app.

Three edits only:
  1. app/src/main/AndroidManifest.xml               -> register EditorActivity
  2. app/src/main/java/com/termux/app/TermuxInstaller.java -> install $PREFIX/bin/eg
  3. app/src/main/java/com/termux/app/TermuxService.java   -> start EgWatcher
Nothing else in the upstream source is touched.
"""
import os
import sys

root = sys.argv[1] if len(sys.argv) > 1 else "src"

# ---------------------------------------------------------------- manifest
mf = os.path.join(root, "app/src/main/AndroidManifest.xml")
s = open(mf, encoding="utf-8").read()
if "EditorActivity" not in s:
    entry = (
        '\n        <activity\n'
        '            android:name=".app.EditorActivity"\n'
        '            android:exported="true"\n'
        '            android:launchMode="singleTop"\n'
        '            android:configChanges="orientation|screenSize|keyboardHidden"\n'
        '            android:windowSoftInputMode="adjustResize" />\n'
    )
    idx = s.rfind("</application>")
    if idx == -1:
        raise SystemExit("could not find </application> in manifest")
    s = s[:idx] + entry + s[idx:]
    open(mf, "w", encoding="utf-8").write(s)
    print("patched manifest")
else:
    print("manifest already patched")

# ---------------------------------------------------------------- installer (writes the eg command)
ti = os.path.join(root, "app/src/main/java/com/termux/app/TermuxInstaller.java")
s = open(ti, encoding="utf-8").read()
if "installEgCommand" in s:
    print("installer already patched")
else:
    early = ("            } else {\n"
             "                whenDone.run();\n"
             "                return;\n"
             "            }")
    early_new = ("            } else {\n"
                 "                installEgCommand();\n"
                 "                whenDone.run();\n"
                 "                return;\n"
                 "            }")
    if early not in s:
        raise SystemExit("early-return anchor not found")
    s = s.replace(early, early_new, 1)

    done = 'Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");'
    done_new = (done + "\n                    installEgCommand();")
    if done not in s:
        raise SystemExit("success anchor not found")
    s = s.replace(done, done_new, 1)

    method = '''    /** Installs the `eg <filename>` helper command into $PREFIX/bin. */
    static void installEgCommand() {
        try {
            File binDir = new File(TERMUX_PREFIX_DIR, "bin");
            if (!binDir.exists()) return;
            File eg = new File(binDir, "eg");
            String script =
                "#!/data/data/com.termux/files/usr/bin/sh\\n" +
                "if [ -z \\"$1\\" ]; then\\n" +
                "  echo \\"usage: eg <filename>    e.g. eg fast.py\\"\\n" +
                "  exit 1\\n" +
                "fi\\n" +
                "case \\"$1\\" in\\n" +
                "  /*) T=\\"$1\\" ;;\\n" +
                "  *)  T=\\"$(pwd)/$1\\" ;;\\n" +
                "esac\\n" +
                "echo \\"$T|$$\\" > \\"$HOME/.eg_open\\"\\n" +
                "echo \\"Editor khul raha hai: $T\\"\\n";
            FileOutputStream out = new FileOutputStream(eg);
            out.write(script.getBytes("UTF-8"));
            out.close();
            Os.chmod(eg.getAbsolutePath(), 0700);
            Logger.logInfo(LOG_TAG, "eg command installed at " + eg.getAbsolutePath());
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to install eg command", e);
        }
    }

'''
    marker = "    public static byte[] loadZipBytes() {"
    if marker not in s:
        raise SystemExit("loadZipBytes marker not found")
    s = s.replace(marker, method + marker, 1)
    open(ti, "w", encoding="utf-8").write(s)
    print("patched installer")

# ---------------------------------------------------------------- service (start the watcher)
ts = os.path.join(root, "app/src/main/java/com/termux/app/TermuxService.java")
s = open(ts, encoding="utf-8").read()
if "EgWatcher" in s:
    print("service already patched")
else:
    old = ('    public void onCreate() {\n'
           '        Logger.logVerbose(LOG_TAG, "onCreate");\n'
           '        runStartForeground();\n'
           '    }')
    new = ('    public void onCreate() {\n'
           '        Logger.logVerbose(LOG_TAG, "onCreate");\n'
           '        runStartForeground();\n'
           '        EgWatcher.start(this);\n'
           '    }')
    if old not in s:
        raise SystemExit("TermuxService.onCreate anchor not found")
    s = s.replace(old, new, 1)
    open(ts, "w", encoding="utf-8").write(s)
    print("patched service")

print("done")
