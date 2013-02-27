package com.joelsoncarl.androidremote;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.AsyncTask;
import android.widget.EditText;
import android.widget.TextView;

/**
 * This handles the RFB Protocol.  The reference document used
 * during implementation was at http://www.realvnc.com/docs/rfbproto.pdf
 */
public class RfbClient {

    /** The TextView used for displaying messages. */
    public TextView m_connectMsg;
    /** A reference to main activity from whence we came */
    public MainActivity m_mainActivity;

    /** Available states and a variable to hold the current state */
    private enum State {
        DISCONNECTED, CONNECTED, CONNECTING
    }
    private State m_state;
    /** Socket connection to the RFB Server */
    /** The IP Address and Port Number of the RFB Server */
    private String m_ip, m_port;
    private Socket m_rfbServerSock;
    /** Input Stream for reading data from the RFB Server */
    private DataInputStream m_rfbInput;
    /** Output Stream for sending data to the RFB Server */
    private DataOutputStream m_rfbOutput;
    /** Holds data to send or data read from the server */
    private byte [] m_data;
    /** Boolean indicator of read/send success */
    private boolean m_dataResult;
    /** Holds the length of strings returned from the server */
    private int m_reasonLength;
    /** Holds the length of the name of the server machine */
    private int m_nameLength;
    /** Record the button states */
    private boolean m_leftButtonDown, m_rightButtonDown;
    
    /** Hold the frame buffer information */
    private int m_fbWidth, m_fbHeight;

    /** Holds the RFB Protocol Version */
    private short m_protocolVersion;
    /** Holds the RFB Security Type */
    private int m_securityType;
    /** Holds the number of security types */
    private short m_numberSecurityTypes;

    /** Security Type Constants */
    private static final int SECURITY_TYPE_INVALID = 0;
    private static final int SECURITY_TYPE_NONE = 1;
    private static final int SECURITY_TYPE_VNC_AUTHENTICATION = 2;

    /** Security Result Constants */
    private static final int SECURITY_RESULT_SUCCESSFUL = 0;
    private static final int SECURITY_RESULT_FAILED = 1;
    
    /** PointerEvent Constants */
    private static final byte POINTER_EVENT_TYPE = 0x05;
    public static final int LEFT_BUTTON = 0;
    public static final int RIGHT_BUTTON = 1;
    public static final byte BUTTON_DOWN = (byte) 0xFF;
    public static final byte BUTTON_UP = 0x00;
    private static final byte LEFT_BUTTON_MASK = 0x01;
    private static final byte RIGHT_BUTTON_MASK = 0x04;

    /**
     * Constructor for RfbClient
     * @param ma The MainActivity from whence we came
     */
    RfbClient(MainActivity ma) {
        m_mainActivity = ma;
        m_connectMsg = (TextView) m_mainActivity.findViewById(R.id.connection_message);
        m_state = State.DISCONNECTED;
        m_rfbServerSock = null;
        m_ip = null;
        m_port = null;
        m_protocolVersion = 0;
        m_data = new byte [64];
        m_dataResult = false;
        m_securityType = 0;
        m_leftButtonDown = false;
        m_rightButtonDown = false;
    }

    /**
     * Opens the socket connection
     */
    public void openConnection() {
        // If currently disconnected, initiate connecting
        if (m_state == State.DISCONNECTED) {
            m_state = State.CONNECTING;
            String ip, port;
            EditText view = (EditText) m_mainActivity.findViewById(R.id.IP_address_entry);
            ip = view.getText().toString();
            view = (EditText) m_mainActivity.findViewById(R.id.port_number_entry);
            port = view.getText().toString();
            if (parseIpAndPort(ip, port)) {
                m_ip = ip;
                m_port = port;
                new RfbConnectTask().execute();
            }
            else {
                m_connectMsg.setText(m_mainActivity.getResources().getString(R.string.ip_port_parse_error));
                m_state = State.DISCONNECTED;
            }
        }
    }
    
    /**
     * Parses the provided IP Address and Port Number and returns
     * true if they are ok, false otherwise
     */
    private boolean parseIpAndPort(String ip, String port) {
        boolean ipParsed = false;
        boolean portParsed = false;
        
        // Parse the IP Address
        Pattern pattern = Pattern.compile("\\b(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");
        Matcher matcher = pattern.matcher(ip);
        if (matcher.find()) {
            ipParsed = true;
        }
        
        // Parse the Port Number
        pattern = Pattern.compile("\\b([1-9]|[1-9][0-9]|[1-9][0-9][0-9]|[1-9][0-9][0-9][0-9]|[1-5][0-9][0-9][0-9][0-9]|6[0-4][0-9][0-9][0-9]|65[0-4][0-9][0-9]|655[0-2][0-9]|6553[0-5])\\b");
        matcher = pattern.matcher(port);
        if (matcher.find()) {
            portParsed = true;
        }

        return ipParsed && portParsed;
    }
    
    /**
     * Closes the input/output streams and the socket
     */
    public void closeConnection() {
        if (m_state == State.CONNECTED) { 
            try {
                if (m_rfbInput != null) {
                    m_rfbInput.close();
                }
                if (m_rfbOutput != null) {
                    m_rfbOutput.close();
                }
                if (m_rfbServerSock != null) {
                    m_rfbServerSock.close();
                }
                m_connectMsg.setText(m_mainActivity.getResources().getString(R.string.disconnected));
            } catch (IOException e) {
                m_connectMsg.setText("Error closing connection");
            }
            m_state = State.DISCONNECTED;
        }
    }
    
    /**
     * Called from the AsyncTask that established the socket connection
     * @param sock The Socket connection to the RFB Server
     */
    public void connectDone(Socket sock) {
        m_rfbServerSock = sock;
        // If socket is not null, we're connected, so open I/O streams
        if (m_rfbServerSock != null) {
            m_state = State.CONNECTED;
            m_connectMsg.setText(m_mainActivity.getResources().getString(R.string.connected));
            try {
                m_rfbInput = new DataInputStream(m_rfbServerSock.getInputStream());
                m_rfbOutput = new DataOutputStream(m_rfbServerSock.getOutputStream());
                new RfbHandshakeTask().execute(0, 12);
            }
            catch (IOException e) {
                m_connectMsg.setText("Error in input or output stream");
                closeConnection();
            }
        }
        // If socket is null, there was a connection error; we are still disconnected
        else {
            m_connectMsg.setText("RFB Socket Connection Error");
            m_state = State.DISCONNECTED;
        }
    }

    /**
     * Establishes the socket connection to the RFB Server
     */
    private class RfbConnectTask extends AsyncTask<Void, String, Socket> {
        protected Socket doInBackground(Void... voids) {
            Socket rfbServerSock = null;
            // Try connecting the socket
            try {
                publishProgress(m_mainActivity.getResources().getString(R.string.connection_progress));
                rfbServerSock = new Socket();
                rfbServerSock.connect(new InetSocketAddress(m_ip, Integer.valueOf(m_port)), 5000);
            }
            catch (IOException e) {
                // If socket fails, try closing it just in case
                try {
                    rfbServerSock.close();
                } catch (IOException e2) {
                    // Do nothing
                }
                rfbServerSock = null;
            }
            return rfbServerSock;
        }

        protected void onProgressUpdate(String... progress) {
            m_connectMsg.setText(progress[0]);
        }

        protected void onPostExecute(Socket sock) {
            connectDone(sock);
        }
    }
    
    /**
     * Calls a task to send the PointerEvent to the RFB Server
     * @param button - left or right
     * @param buttonDown - indicates if button is down
     * @param x - x-coordinate
     * @param y - y-coordinate
     */
    public void mouseEvent(int button, boolean buttonDown, int x, int y) {
        // Set the message type
        m_data[0] = POINTER_EVENT_TYPE;
        // Setup the button mask
        byte buttonMask = 0x00;
        // Set button mask to keep the left and right in
        // their previous state
        if (m_leftButtonDown) {
            buttonMask = (byte) (0xFF & (buttonMask | LEFT_BUTTON_MASK));
        }
        if (m_rightButtonDown) {
            buttonMask = (byte) (0xFF & (buttonMask | RIGHT_BUTTON_MASK));
        }
        // If we are pushing a button down, set the corresponding bit
        if (buttonDown) {
            if (button == LEFT_BUTTON) {
                buttonMask = (byte) (0xFF & (buttonMask | LEFT_BUTTON_MASK));
                m_leftButtonDown = true;
            }
            else {
                buttonMask = (byte) (0xFF & (buttonMask | RIGHT_BUTTON_MASK));
                m_rightButtonDown = true;
            }
        }
        // Otherwise clear the bit to indicate it going up
        else {
            if (button == LEFT_BUTTON) {
                buttonMask = (byte) (0xFF & (buttonMask & ~LEFT_BUTTON_MASK));
                m_leftButtonDown = false;
            }
            else {
                buttonMask = (byte) (0xFF & (buttonMask & ~RIGHT_BUTTON_MASK));
                m_rightButtonDown = false;
            }
        }
        m_data[1] = buttonMask;
        m_data[2] = (byte) ((0xFF00 & x) >> 8);
        m_data[3] = (byte) (0xFF & x);
        m_data[4] = (byte) ((0xFF00 & y) >> 8);
        m_data[5] = (byte) (0xFF & y);
        new RfbPointerEventTask().execute(0, 6);
    }

    /**
     * Generic AsyncTask for reading data from the RFB Server
     * 
     * Argument to execute() should be an Integer array with
     * the first element being the offset of where to start
     * storing data in the byte array, and the second element
     * being the number of bytes to read.
     */
    private class RfbReadDataTask extends AsyncTask<Integer, Void, Boolean> {
        protected Boolean doInBackground(Integer... ints) {
            try {
                m_rfbInput.readFully(m_data, ints[0], ints[1]);
            } catch (IOException e) {
                m_connectMsg.setText("Error: RfbReadDataTask()");
                return false;
            }
            return true;
        }

        protected void onPostExecute(Boolean result) {
            m_dataResult = result;
        }
    }

    /**
     * Generic AsyncTask for sending data to the RFB Server
     * 
     * Argument to execute() should be an Integer array with
     * the first element being the offset of where to start
     * reading data in the byte array, and the second element
     * being the number of bytes to send.
     */
    private class RfbSendDataTask extends AsyncTask<Integer, Void, Boolean> {
        protected Boolean doInBackground(Integer... ints) {
            try {
                m_rfbOutput.write(m_data, ints[0], ints[1]);
            } catch (IOException e) {
                m_connectMsg.setText("Error: RfbSendDataTask()");
                return false;
            }
            return true;
        }

        protected void onPostExecute(Boolean result) {
            m_dataResult = result;
        }
    }
    
    /**
     * Sends the PointerEvent to the RFB Server
     */
    private class RfbPointerEventTask extends RfbSendDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!m_dataResult) {
                m_connectMsg.setText("Error Sending RFB Pointer Event");
            }
        }
    }

    /**
     * This handles reading the initial handshake message from
     * the RFB server ("RFB 003.00x\n" where 'x' is 3, 7, or 8).
     * Grabs the 'x' to record the protocol version.
     */
    private class RfbHandshakeTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                //Uncomment line below to force test protocol 3.3
                //(assumes we were getting 3.8)
                //m_data[10] = (byte) (m_data[10] - 5);
                m_protocolVersion = (short)((0xFF & ((int) m_data[10])) - 0x30);
                m_connectMsg.setText("Protocol Version: " + Short.toString(m_protocolVersion));
                new RfbHandshakeResponseTask().execute(0, 12);
            }
            else {
                m_connectMsg.setText("Error Receiving RFB Handshake");
            }
        }
    }

    /**
     * Responds by echoing back the handshake information (protocol allows
     * selecting a lower version, but we will just use the highest provided
     * by the server)
     */
    private class RfbHandshakeResponseTask extends RfbSendDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                // Version 3.7 onwards - The server sends a single
                // byte with the number of security types supported,
                // so we need to grab that single byte first
                if (m_protocolVersion >= 7) {
                    new RfbSecurityTask().execute(0, 1);
                }
                // Version 3.3 - The server decides the security type
                // and sends a single 4 byte word.
                else {
                    new RfbSecurityTask().execute(0, 4);
                }
            }
            else {
                m_connectMsg.setText("Error Sending RFB Handshake Response");
            }
        }
    }

    /**
     * Reads the number of security types (3.7+) or the server-dictated
     * security type (3.3)
     */
    private class RfbSecurityTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                // Version 3.7+
                if (m_protocolVersion >= 7) {
                    m_numberSecurityTypes = (short) (0xFF & ((int) m_data[0]));
                    m_connectMsg.setText("numberSecurityTypes = " + Short.toString(m_numberSecurityTypes));
                    // A number of security types value of 0 means failure,
                    // and the server will be sending us a reason string
                    if (m_numberSecurityTypes == 0) {
                        new RfbFailureReasonLengthTask().execute(0, 4);
                    }
                    // We need to read the array of bytes to get the list
                    // of security types supported
                    else {
                        new RfbSecurityTypesTask().execute(0, (int) m_numberSecurityTypes);
                    }
                }
                // Version 3.3
                else {
                    // Combine the four bytes into a single int representing
                    // the security type chosen by the server
                    short securityTypeByte3 = (short) (0xFF & ((int) m_data[0]));
                    short securityTypeByte2 = (short) (0xFF & ((int) m_data[1]));
                    short securityTypeByte1 = (short) (0xFF & ((int) m_data[2]));
                    short securityTypeByte0 = (short) (0xFF & ((int) m_data[3]));
                    int securityType;
                    securityType = (securityTypeByte3 << 24)
                                 | (securityTypeByte2 << 16)
                                 | (securityTypeByte1 << 8)
                                 | (securityTypeByte0);
                    m_connectMsg.setText("securityType = " + Integer.toString(securityType));
                    // A type of 0 indicates failure, and the server will
                    // be sending a reason string
                    if (securityType == SECURITY_TYPE_INVALID) {
                        new RfbFailureReasonLengthTask().execute(0, 4);
                    }
                    else {
                        m_securityType = securityType;
                        // TODO: Continue 3.3 Rfb Security Process
                        new RfbSecurityTypesTask().execute(0, 0);
                    }
                }
            }
            else {
                m_connectMsg.setText("Error Receiving Security Information");
            }
        }
    }

    /**
     * Version 3.7+ - Read the security types available
     * Version 3.3 - Nothing to do; go on to next task
     */
    private class RfbSecurityTypesTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                // Version 3.7 onwards - The server lists the security
                // types supported
                if (m_protocolVersion >= 7) {
                    boolean secTypeInvalid = false;
                    boolean secTypeNone = false;
                    boolean secTypeVNCAuthentication = false;
                    // Mark the found security types valid
                    for (int i = 0; i < m_numberSecurityTypes; i++) {						
                        switch ((int) m_data[i]) {
                        case SECURITY_TYPE_INVALID:
                            // Invalid value (shouldn't be reached... I think)
                            secTypeInvalid = true;
                            break;
                        case SECURITY_TYPE_NONE:
                            // No security type
                            secTypeNone = true;
                            break;
                        case SECURITY_TYPE_VNC_AUTHENTICATION:
                            // VNC Authentication
                            secTypeVNCAuthentication = true;
                            break;
                        default:
                            // Shouldn't reach here
                            break;
                        }
                    }
                    // Choose one of the found security types (pre-defined choice order)
                    if (secTypeVNCAuthentication) {
                        m_securityType = SECURITY_TYPE_VNC_AUTHENTICATION;
                        m_data[0] = (byte) (0xFF & m_securityType);
                        new RfbSendSecurityChoiceTask().execute(0, 1);
                    }
                    else if (secTypeNone) {
                        m_securityType = SECURITY_TYPE_NONE;
                        m_data[0] = (byte) (0xFF & m_securityType);
                        new RfbSendSecurityChoiceTask().execute(0, 1);
                    }
                    else if (secTypeInvalid) {
                        // Invalid
                        m_connectMsg.setText("Server reported Invalid security type option");
                    }
                    else {
                        // Error, no security choice available
                        m_connectMsg.setText("Error: No security options supported");
                    }
                }
                // Version 3.3 - We were already given the security type from the server
                else {
                    new RfbSendSecurityChoiceTask().execute(0, 0);
                }
            }
            else {
                m_connectMsg.setText("Error Receiving Security Information (part 2)");
            }
        }
    }

    /**
     * Version 3.7+ - Sends the chosen security type to the server, then proceed based
     *                on the security type
     * Version 3.3 - Proceed based on the security type
     */
    private class RfbSendSecurityChoiceTask extends RfbSendDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                // No Authentication, No Encryption
                if (m_securityType == SECURITY_TYPE_NONE) {
                    // The protocol continues with the SecurityResult message
                    if (m_protocolVersion >= 8) {
                        new RfbSecurityResultTask().execute(0, 4);
                    }
                    // The protocol passes to the initialization phase
                    else {
                        new RfbInitializationTask().execute(0, 0);
                    }
                }
                else if (m_securityType == SECURITY_TYPE_VNC_AUTHENTICATION) {
                    // TODO: Implement
                    /*
					VNC authentication is to be used and protocol data is to be sent unencrypted.
					The	server sends a random 16-byte challenge:
					No. of bytes Type [Value] Description
					16           U8           challenge
					The client encrypts the challenge with DES, using a password supplied by the
					user as the key, and sends the resulting 16-byte response:
					No. of bytes Type [Value] Description
					16           U8           response
					The protocol continues with the SecurityResult message.
                     */
                }
            }
            else {
                m_connectMsg.setText("Error Sending RFB Security Choice");
            }
        }
    }

    /**
     * Reads the security result from the server
     */
    private class RfbSecurityResultTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                short securityResultByte3 = (short) (0xFF & ((int) m_data[0]));
                short securityResultByte2 = (short) (0xFF & ((int) m_data[1]));
                short securityResultByte1 = (short) (0xFF & ((int) m_data[2]));
                short securityResultByte0 = (short) (0xFF & ((int) m_data[3]));
                int securityResult;
                securityResult = (securityResultByte3 << 24) 
                               | (securityResultByte2 << 16)
                               | (securityResultByte1 << 8)
                               | (securityResultByte0);
                m_connectMsg.setText("securityResult = " + Integer.toString(securityResult));
                if (securityResult == SECURITY_RESULT_SUCCESSFUL) {
                    // If successful, the protocol passes to the initialization phase
                    new RfbInitializationTask().execute(0, 0);
                }
                else if (securityResult == SECURITY_RESULT_FAILED) {
                    if (m_protocolVersion >= 8) {
                        // The server sends a string describing the reason for
                        // the failure, then closes the connection
                        new RfbFailureReasonLengthTask().execute(0, 4);
                    }
                    else {
                        // The server closes the connection
                    }
                }
                else {
                    m_connectMsg.setText("Error... Security Result = " + Integer.toString(securityResult));
                }
            }
            else {
                m_connectMsg.setText("Error Receiving Security Result");
            }
        }
    }

    /**
     * Starts the Initialization Phase by immediately going to the ClientInit
     */
    private class RfbInitializationTask extends RfbSendDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                short sharedFlag = 0;
                m_data[0] = (byte) (0xFF & sharedFlag);
                new RfbClientInitTask().execute(0, 1);
            }
            else {
                m_connectMsg.setText("Error in RFB Initialization Task");
            }
        }
    }

    /**
     * Sends the ClientInit message, then proceeds to ServerInit
     */
    private class RfbClientInitTask extends RfbSendDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                new RfbServerInitTask().execute(0, 24);
            }
            else {
                m_connectMsg.setText("Error sending ClientInit message");
            }
        }
    }

    /**
     * Reads the ServerInit message through the name-length, then calls
     * the task to read the name-string
     */
    private class RfbServerInitTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                // Grab the framebuffer width and height
                short fbByte1 = (short) (0xFF & ((int) m_data[0]));
                short fbByte0 = (short) (0xFF & ((int) m_data[1]));
                m_fbWidth = (fbByte1 << 8)
                          | (fbByte0);
                fbByte1 = (short) (0xFF & ((int) m_data[2]));
                fbByte0 = (short) (0xFF & ((int) m_data[3]));
                m_fbHeight = (fbByte1 << 8)
                           | (fbByte0);
                // Grab the name length
                short nameLengthByte3 = (short) (0xFF & ((int) m_data[20]));
                short nameLengthByte2 = (short) (0xFF & ((int) m_data[21]));
                short nameLengthByte1 = (short) (0xFF & ((int) m_data[22]));
                short nameLengthByte0 = (short) (0xFF & ((int) m_data[23]));
                int nameLength = 0;
                nameLength = (nameLengthByte3 << 24)
                           | (nameLengthByte2 << 16)
                           | (nameLengthByte1 << 8)
                           | (nameLengthByte0);
                m_nameLength = nameLength;
                new RfbServerInitNameTask().execute(0, nameLength);
            }
            else {
                m_connectMsg.setText("Error Receiving Server Init");
            }
        }
    }
    
    /**
     * Read the name of the machine of the server; connection is complete after this
     */
    private class RfbServerInitNameTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < m_nameLength; i++) {
                    s.append((char) m_data[i]);
                }
                m_connectMsg.setText("Connected to " + s.toString() + ", " + Integer.toString(m_fbWidth) + "x" + Integer.toString(m_fbHeight));
            }
            else {
                m_connectMsg.setText("Error Receiving Server Init Machine Name");
            }
        }
    }

    /**
     * There was a failure and the server is sending a reason string.
     * We first need to read the 4 byte word that tells how long the
     * reason string will be.
     */
    private class RfbFailureReasonLengthTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                short failureReasonLengthByte3 = (short) (0xFF & ((int) m_data[0]));
                short failureReasonLengthByte2 = (short) (0xFF & ((int) m_data[1]));
                short failureReasonLengthByte1 = (short) (0xFF & ((int) m_data[2]));
                short failureReasonLengthByte0 = (short) (0xFF & ((int) m_data[3]));
                int failureReasonLength = 0;
                failureReasonLength = (failureReasonLengthByte3 << 24)
                                    | (failureReasonLengthByte2 << 16)
                                    | (failureReasonLengthByte1 << 8)
                                    | (failureReasonLengthByte0);
                m_reasonLength = failureReasonLength;
                new RfbFailureReasonTask().execute(0, failureReasonLength);
            }
            else {
                m_connectMsg.setText("Error Receiving Failure Reason Length");
            }
        }
    }

    /**
     * Read the actual reason string
     */
    private class RfbFailureReasonTask extends RfbReadDataTask {
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (m_dataResult) {
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < m_reasonLength; i++) {
                    s.append((char) m_data[i]);
                }
                m_connectMsg.setText(s.toString());
            }
            else {
                m_connectMsg.setText("Error Receiving Failure Reason");
            }
        }
    }

}
