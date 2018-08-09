package itx.examples.sshd.commands.repl;

import itx.examples.sshd.commands.CommandProcessor;
import itx.examples.sshd.commands.keymaps.KeyMap;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;

public class REPLCommandProcessor implements Runnable {

    final private static Logger LOG = LoggerFactory.getLogger(REPLCommandProcessor.class);

    final private static String CMD_EXIT = "exit";

    private InputStream stdin;
    private OutputStream stdout;
    private OutputStream stderr;
    private ExitCallback exitCallback;
    private CommandProcessor commandProcessor;
    private KeyMap keyMap;
    private String prompt;
    private Charset charset;

    public REPLCommandProcessor(String prompt, KeyMap keyMap, CommandProcessor commandProcessor,
                                InputStream stdin, OutputStream stdout, OutputStream stderr, ExitCallback exitCallback) {
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCallback = exitCallback;
        this.commandProcessor = commandProcessor;
        this.keyMap = keyMap;
        this.prompt = prompt;
        this.charset = Charset.forName("UTF-8");
    }

    @Override
    public void run() {
        try {
            Reader r = new InputStreamReader(stdin, charset);
            int intch;
            CommandRenderer commandRenderer = new CommandRenderer();
            writePrompt();
            while ((intch = r.read()) != -1) {
                char ch = (char) intch;
                if (intch == keyMap.getEnterKeyCode()) {
                    //on ENTER
                    stdout.write('\n');
                    stdout.write('\r');
                    if (!processCommand(commandRenderer.getCommandAndReset())) {
                        return;
                    }
                    writePrompt();
                } else if (intch == keyMap.getBackSpaceKeyCode()) {
                    //on BACKSPACE
                    writeBlanks(commandRenderer.getCommand());
                    commandRenderer.onBackSpace();
                    renderCommandline(commandRenderer);
                    stdout.flush();
                } else if (intch == keyMap.getArrowPrefix()) {
                    //arrow keys handling
                    int key1 = r.read();
                    int key2 = r.read();
                    if (keyMap.isKeyLeftSequence(intch, key1, key2)) {
                        commandRenderer.onKeyLeft();
                    } else if (keyMap.isKeyRightSequence(intch, key1, key2)) {
                        commandRenderer.onKeyRight();
                    } else {
                        LOG.error("Unsupported arrow key sequence {} {} {}", intch, key1, key2);
                    }
                    stdout.write(intch);
                    stdout.write(key1);
                    stdout.write(key2);
                    stdout.flush();
                } else {
                    //on normal character
                    commandRenderer.onCharInsert(ch);
                    renderCommandline(commandRenderer);
                    stdout.flush();
                }
            }
        } catch (IOException e) {
            LOG.error("ERROR: ", e);
        }
    }

    private void renderCommandline(CommandRenderer commandRenderer) throws IOException {
        stdout.write('\r');
        stdout.write((prompt + commandRenderer.getCommand()).getBytes(charset));
        stdout.write('\r');
        moveRight(prompt.length() + commandRenderer.getCursorPosition());
    }

    private void writePrompt() throws IOException {
        stdout.write(prompt.getBytes(charset));
        stdout.flush();
    }

    private void writeBlanks(String command) throws IOException {
        stdout.write('\r');
        stdout.write(prompt.getBytes(charset));
        for (int i=0; i<command.length(); i++) {
            stdout.write(' ');
        }
    }

    private void moveRight(int cursorPosition) throws IOException {
        for (int i=0; i<cursorPosition; i++) {
            int[] keyRightSequence = keyMap.getKeyRightSequence();
            for (int j=0; j<keyRightSequence.length; j++) {
                stdout.write(keyRightSequence[j]);
            }
        }
    }

    private boolean processCommand(String command) throws IOException {
        command = command.trim();
        if (CMD_EXIT.equals(command)) {
            LOG.info("on exit");
            exitCallback.onExit(0);
            return false;
        } else {
            commandProcessor.processCommand(command, stdout, stderr);
            stdout.flush();
            stderr.flush();
            return true;
        }
    }

}
