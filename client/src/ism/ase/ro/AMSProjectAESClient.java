package ism.ase.ro;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import java.util.Properties;
import java.util.LinkedList;
import java.util.stream.IntStream;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

import com.oracle.javacard.ams.AMService;
import com.oracle.javacard.ams.AMServiceFactory;
import com.oracle.javacard.ams.AMSession;
import com.oracle.javacard.ams.config.AID;
import com.oracle.javacard.ams.config.CAPFile;
import com.oracle.javacard.ams.script.APDUScript;
import com.oracle.javacard.ams.script.ScriptFailedException;
import com.oracle.javacard.ams.script.Scriptable;

public class AMSProjectAESClient {

    static final String isdAID = "aid:A000000151000000";
    static final String sAID_CAP = "aid:A00000006203010D07";
    static final String sAID_AppletClass = "aid:A00000006203010D0701";
    static final String sAID_AppletInstance = "aid:A00000006203010D0701";
    static final CommandAPDU selectApplet = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, AID.from(sAID_AppletInstance).toBytes(), 256);

    public static void main(String[] args) {
        try {
            CAPFile appFile = CAPFile.from(getArg(args, "cap"));
            Properties props = new Properties();
            props.load(new FileInputStream(getArg(args, "props")));

            AMService ams = AMServiceFactory.getInstance("GP2.2");
            ams.setProperties(props);
            for (String key : ams.getPropertiesKeys()) {
				System.out.println(key + " = " + ams.getProperty(key));
			}

            AMSession deploy = ams.openSession(isdAID)
                .load(sAID_CAP, appFile.getBytes())
                .install(sAID_CAP, sAID_AppletClass, sAID_AppletInstance, new byte[] {0x05, 0x01, 0x02, 0x03, 0x04, 0x05});

            TestScript testScript = new TestScript()
            .append(deploy)
            .append(selectApplet)
            .append(new CommandAPDU(0x80, 0x20, 0x00, 0x00, new byte[]{0x05, 0x01, 0x02, 0x03, 0x04, 0x05}),
                    new ResponseAPDU(new byte[]{(byte) 0x90, (byte) 0x00}));
            

            AMSession undeploy = ams.openSession(isdAID)
                .uninstall(sAID_AppletInstance)
                .unload(sAID_CAP)
                .close();

            CardTerminal terminal = getTerminal("socket", "127.0.0.1", "9025");
            if (terminal.waitForCardPresent(10000)) {
                System.out.println("Connection to simulator established: "+ terminal.getName());
                Card card = terminal.connect("*");
                System.out.println(getFormattedATR(card.getATR().getBytes()));
                CardChannel channel = card.getBasicChannel();
                ResponseAPDU response;
                
                testScript.run(channel);

                // Load file to encrypt
                byte[] fileData = readFile("../../input.pdf");
                fileData = addPadding(fileData);

                // Encrypt file block by block (16 bytes)
                byte[] encrypted = new byte[fileData.length];
                for (int i = 0; i < fileData.length; i += 16) {
                    byte[] block = new byte[16];
                    System.arraycopy(fileData, i, block, 0, Math.min(16, fileData.length - i));
                    System.out.println("Encrypting block " + (i / 16));
                    response = channel.transmit(new CommandAPDU(0x80, 0x30, 0x00, 0x00, block));
                    checkResponse(response);
                    if (response.getData().length != 16) {
                        throw new IOException("Unexpected encrypted block length");
                    }
                    System.arraycopy(response.getData(), 0, encrypted, i, 16);
                }
                writeFile("../../encrypted_output.pdf", encrypted);

                // Decrypt file block by block
                byte[] decrypted = new byte[encrypted.length];
                for (int i = 0; i < encrypted.length; i += 16) {
                    byte[] block = new byte[16];
                    System.arraycopy(encrypted, i, block, 0, 16);
                    response = channel.transmit(new CommandAPDU(0x80, 0x40, 0x00, 0x00, block));
                    checkResponse(response);
                    if (response.getData().length != 16) {
                        throw new IOException("Unexpected encrypted block length");
                    }
                    System.arraycopy(response.getData(), 0, decrypted, i, 16);
                }
                writeFile("../../decrypted_output.pdf", removePadding(decrypted));

                card.disconnect(true);
            } else {
                System.out.println("Card not present.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkResponse(ResponseAPDU response) throws IOException {
        int sw = response.getSW();
        System.out.printf("Response SW: %04X\n", sw);
        if (sw != 0x9000) {
            throw new IOException("Card returned error status: " + Integer.toHexString(sw));
        }
    }

    public static byte[] addPadding(byte[] data) {
        int padLen = 16 - (data.length % 16);
        byte[] padded = new byte[data.length + padLen];
        System.arraycopy(data, 0, padded, 0, data.length);
        for (int i = data.length; i < padded.length; i++) {
            padded[i] = (byte) padLen;
        }
        return padded;
    }

    public static byte[] removePadding(byte[] data) {
        int padLen = data[data.length - 1] & 0xFF;
        if (padLen <= 0 || padLen > 16) {
            throw new IllegalArgumentException("Invalid padding");
        }
        for (int i = data.length - padLen; i < data.length; i++) {
            if ((data[i] & 0xFF) != padLen) {
                throw new IllegalArgumentException("Corrupted padding");
            }
        }
        byte[] unpadded = new byte[data.length - padLen];
        System.arraycopy(data, 0, unpadded, 0, unpadded.length);
        return unpadded;
    }    

    private static byte[] readFile(String filename) throws IOException {
        File file = new File(filename);
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return data;
    }

    private static void writeFile(String filename, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(filename);
        fos.write(data);
        fos.close();
    }

    private static String getArg(String[] args, String argName) {
        for (String param : args) {
            if (param.startsWith("-" + argName + "=")) {
                return param.substring(param.indexOf('=') + 1);
            }
        }
        throw new IllegalArgumentException("Missing argument: " + argName);
    }

    private static String getFormattedATR(byte[] ATR) {
		StringBuilder sb = new StringBuilder();
		for (byte b : ATR) {
			sb.append(String.format("%02X ", b));
		}
		return String.format("ATR: [%s]", sb.toString().trim());
	}

	private static CardTerminal getTerminal(String... connectionParams) throws NoSuchAlgorithmException, NoSuchProviderException, CardException {
		TerminalFactory tf;
		String connectivityType = connectionParams[0];
		if (connectivityType.equals("socket")) {
			String ipaddr = connectionParams[1];
			String port = connectionParams[2];
			tf = TerminalFactory.getInstance("SocketCardTerminalFactoryType",
					List.of(new InetSocketAddress(ipaddr, Integer.parseInt(port))),
					"SocketCardTerminalProvider");
		} else {
			tf = TerminalFactory.getDefault();
		}
		return tf.terminals().list().get(0);
	}

	private static class TestScript extends APDUScript {
		private List<CommandAPDU>  commands = new LinkedList<>();
		private List<ResponseAPDU> responses = new LinkedList<>();
		private int index = 0;

		public List<ResponseAPDU> run(CardChannel channel) throws ScriptFailedException {
			return super.run(channel, c -> lookupIndex(c), r -> !isExpected(r));
		}

		@Override
		public TestScript append(Scriptable<CardChannel, CommandAPDU, ResponseAPDU> other) {
			super.append(other);
			return this;
		}

		public TestScript append(CommandAPDU apdu, ResponseAPDU expected) {
			super.append(apdu);
			this.commands.add(apdu);
			this.responses.add(expected);
			return this;
		}

		public TestScript append(CommandAPDU apdu) {
			super.append(apdu);
			return this;
		}

		private CommandAPDU lookupIndex(CommandAPDU apdu) {
			print(apdu);
			this.index = IntStream.range(0, this.commands.size())
			        .filter(i -> apdu == this.commands.get(i))
					.findFirst()
					.orElse(-1);
			return apdu;
		}

		private boolean isExpected(ResponseAPDU response) {

			ResponseAPDU expected = (index < 0)? response : this.responses.get(index);
			if (!response.equals(expected)) {
				System.out.println("Received: ");
				print(response);
				System.out.println("Expected: ");
				print(expected);
				return false;
			}
			print(response);
			return true;
		}

		private static void print(CommandAPDU apdu) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%02X%02X%02X%02X %02X[", apdu.getCLA(), apdu.getINS(), apdu.getP1(), apdu.getP2(), apdu.getNc()));
			for (byte b : apdu.getData()) {
				sb.append(String.format("%02X", b));
			}
			sb.append("]");
			System.out.format("[%1$tF %1$tT %1$tL %1$tZ] [APDU-C] %2$s %n", System.currentTimeMillis(), sb.toString());
		}

		private static void print(ResponseAPDU apdu) {
			byte[] bytes = apdu.getData();
			StringBuilder sb = new StringBuilder();
			for (byte b : bytes) {
				sb.append(String.format("%02X", b));
			}
			System.out.format("[%1$tF %1$tT %1$tL %1$tZ] [APDU-R] [%2$s] SW:%3$04X %n", System.currentTimeMillis(), sb.toString(), apdu.getSW());
		}
	}
}
