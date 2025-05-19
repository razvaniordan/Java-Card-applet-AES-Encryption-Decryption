package ism.ase.ro;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacard.security.AESKey;
import javacard.security.KeyBuilder;
import javacardx.crypto.Cipher;


public class ProjectAES extends Applet {
 
    /* constants declaration */

    // code of CLA byte in the command APDU header
    final static byte ProjectAES_CLA = (byte) 0x80; 

    // codes of INS byte in the command APDU header
    final static byte VERIFY = (byte) 0x20;
    final static byte ENCRYPT = (byte) 0x30;
    final static byte DECRYPT = (byte) 0x40;

    // maximum number of incorrect tries before the
    // PIN is blocked
    final static byte PIN_TRY_LIMIT = (byte) 0x03;
    // maximum size PIN
    final static byte MAX_PIN_SIZE = (byte) 0x08;

    // signal that the PIN verification failed
    final static short SW_VERIFICATION_FAILED = 0x6300;
    // signal the the PIN validation is required
    // for encryption or decryption
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
    final static short SW_WRONG_DATA_LENGTH = 0x6A87;

    /* instance variables declaration */
    OwnerPIN pin;
    AESKey aesKey;
    Cipher aesEncryptCipher;
    Cipher aesDecryptCipher;

    private static final short AES_BLOCK_SIZE = 16;

    private ProjectAES(byte[] bArray, short bOffset, byte bLength) {

        pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);

        byte iLen = bArray[bOffset]; // aid length
        bOffset = (short) (bOffset + iLen + 1);
        byte cLen = bArray[bOffset]; // info length
        bOffset = (short) (bOffset + cLen + 1);
        byte aLen = bArray[bOffset]; // applet data length

        // The installation parameters contain the PIN
        // initialization value
        pin.update(bArray, (short) (bOffset + 1), aLen);

        // we create AESKey of 128 bit length
        aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);

        // the AES key is static (hardcoded) here of 16 bytes
        byte[] keyBytes = new byte[] {
            (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03,
            (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07,
            (byte)0x08, (byte)0x09, (byte)0x0A, (byte)0x0B,
            (byte)0x0C, (byte)0x0D, (byte)0x0E, (byte)0x0F
        };

        aesKey.setKey(keyBytes, (short) 0);

        // initialize cipher objects
        aesEncryptCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
        aesDecryptCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);

        // init both ciphers with the same key
        aesEncryptCipher.init(aesKey, Cipher.MODE_ENCRYPT);
        aesDecryptCipher.init(aesKey, Cipher.MODE_DECRYPT);

        register();
    } // end of the constructor

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        // create a ProjectAES applet instance
        new ProjectAES(bArray, bOffset, bLength);
    } // end of install method

    @Override
    public boolean select() {

        // The applet declines to be selected
        // if the pin is blocked.
        if (pin.getTriesRemaining() == 0) {
            return false;
        }

        return true;

    }// end of select method

    @Override
    public void deselect() {

        // reset the pin value
        pin.reset();

    }

    @Override
    public void process(APDU apdu) {

        // APDU object carries a byte array (buffer) to
        // transfer incoming and outgoing APDU header
        // and data bytes between card and CAD

        // At this point, only the first header bytes
        // [CLA, INS, P1, P2, P3] are available in
        // the APDU buffer.
        // The interface javacard.framework.ISO7816
        // declares constants to denote the offset of
        // these bytes in the APDU buffer

        byte[] buffer = apdu.getBuffer();
        // check SELECT APDU command

        if (apdu.isISOInterindustryCLA()) {
            if (buffer[ISO7816.OFFSET_INS] == (byte) (0xA4)) {
                return;
            }
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        // verify the reset of commands have the
        // correct CLA byte, which specifies the
        // command structure
        if (buffer[ISO7816.OFFSET_CLA] != ProjectAES_CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
            case ENCRYPT:
                encrypt(apdu);
                return;
            case DECRYPT:
                decrypt(apdu);
                return;
            case VERIFY:
                verify(apdu);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }

    } // end of process method

    private void encrypt(APDU apdu) {

        // access authentication
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }

        byte[] buffer = apdu.getBuffer();

        // Lc byte denotes the number of bytes in the
        // data field of the command APDU
        byte numBytes = buffer[ISO7816.OFFSET_LC];

        // indicate that this APDU has incoming data
        // and receive data starting from the offset
        // ISO7816.OFFSET_CDATA following the 5 header
        // bytes.
        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        // it is an error if the number of data bytes
        // read does not match the number in Lc byte
        if (byteRead != AES_BLOCK_SIZE) ISOException.throwIt(SW_WRONG_DATA_LENGTH);

        short offset = ISO7816.OFFSET_CDATA;
        aesEncryptCipher.doFinal(buffer, offset, AES_BLOCK_SIZE, buffer, (short) 0);

        apdu.setOutgoingAndSend((short) 0, AES_BLOCK_SIZE);

    } // end of deposit method

    private void decrypt(APDU apdu) {

        // access authentication
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }

        byte[] buffer = apdu.getBuffer();

        byte numBytes = (buffer[ISO7816.OFFSET_LC]);

        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        if (byteRead != AES_BLOCK_SIZE) ISOException.throwIt(SW_WRONG_DATA_LENGTH);

        short offset = ISO7816.OFFSET_CDATA;
        aesDecryptCipher.doFinal(buffer, offset, AES_BLOCK_SIZE, buffer, (short) 0);

        apdu.setOutgoingAndSend((short) 0, AES_BLOCK_SIZE);

    } // end of debit method 

    private void verify(APDU apdu) {

        byte[] buffer = apdu.getBuffer();
        // retrieve the PIN data for validation.
        byte byteRead = (byte) (apdu.setIncomingAndReceive());

        // check pin
        // the PIN data is read into the APDU buffer
        // at the offset ISO7816.OFFSET_CDATA
        // the PIN data length = byteRead
        if (pin.check(buffer, ISO7816.OFFSET_CDATA, byteRead) == false) {
            ISOException.throwIt(SW_VERIFICATION_FAILED);
        }

    } // end of validate method
} // end of class Wallet