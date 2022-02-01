package com.beboe.pioneerremote;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.Editable;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.SimpleOptionHandler;
import org.apache.commons.net.telnet.SuppressGAOptionHandler;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetNotificationHandler;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity implements Runnable, TelnetNotificationHandler {
    private static TelnetClient tc;
    String command = null;
    EditText commandText;
    EditText hostnameText;
    TextView extResponse;
    String remoteip;
    int remoteport;
    FileOutputStream fout = null;
    String line;
    public String updateText(){
        command = commandText.getText().toString();
        commandText.setText("");
        extResponse.append("\n" + command);
        return command;
    }

    public void connectButton(View view){
        remoteip = hostnameText.getText().toString();
        extResponse.append("\n" + remoteip + ":" + remoteport + " is connecting...");
        this.connectClient();
    }

    public void connectClient(){
        while (true)
        {
            boolean end_loop = false;
            try
            {
                tc.connect(remoteip, remoteport);


                final Thread reader = new Thread (new MainActivity());
                tc.registerNotifHandler(new MainActivity());
                extResponse.setTextKeepState("TelnetClientExample\n");
                extResponse.setTextKeepState("Type AYT to send an AYT telnet command\n");
                extResponse.setTextKeepState("Type OPT to print a report of status of options (0-24)\n");
                extResponse.setTextKeepState("Type REGISTER to register a new SimpleOptionHandler\n");
                extResponse.setTextKeepState("Type UNREGISTER to unregister an OptionHandler\n");
                extResponse.setTextKeepState("Type SPY to register the spy (connect to port 3333 to spy)\n");
                extResponse.setTextKeepState("Type UNSPY to stop spying the connection\n");
                extResponse.setTextKeepState("Type ^[A-Z] to send the control character; use ^^ to send ^\n");

                reader.start();
                final OutputStream outstr = tc.getOutputStream();

                final byte[] buff = new byte[1024];
                int ret_read = 0;

                do
                {
                    try
                    {
                        ret_read = System.in.read(buff);
                        if(ret_read > 0)
                        {

                            line = this.updateText(); // deliberate use of default charset
                            if(line.startsWith("AYT"))
                            {
                                try
                                {
                                    extResponse.setTextKeepState("Sending AYT\n");

                                    extResponse.setTextKeepState("AYT response:" + tc.sendAYT(5000) + "\n");
                                }
                                catch (final IOException e)
                                {
                                    Toast.makeText(getApplicationContext(),"Exception waiting AYT response: " + e.getMessage() + "\n",Toast.LENGTH_SHORT).show();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            else if(line.startsWith("OPT"))
                            {
                                extResponse.setTextKeepState("Status of options:\n");
                                for(int ii=0; ii<25; ii++) {
                                    extResponse.setTextKeepState("Local Option " + ii + ":" + tc.getLocalOptionState(ii) +
                                            " Remote Option " + ii + ":" + tc.getRemoteOptionState(ii) + "\n");
                                }
                            }
                            else if(line.startsWith("REGISTER"))
                            {
                                final StringTokenizer st = new StringTokenizer(new String(buff));
                                try
                                {
                                    st.nextToken();
                                    final int opcode = Integer.parseInt(st.nextToken());
                                    final boolean initlocal = Boolean.parseBoolean(st.nextToken());
                                    final boolean initremote = Boolean.parseBoolean(st.nextToken());
                                    final boolean acceptlocal = Boolean.parseBoolean(st.nextToken());
                                    final boolean acceptremote = Boolean.parseBoolean(st.nextToken());
                                    final SimpleOptionHandler opthand = new SimpleOptionHandler(opcode, initlocal, initremote,
                                            acceptlocal, acceptremote);
                                    tc.addOptionHandler(opthand);
                                }
                                catch (final Exception e)
                                {
                                    if(e instanceof InvalidTelnetOptionException)
                                    {
                                        Toast.makeText(getApplicationContext(),"Error registering option: " + e.getMessage() + "\n",Toast.LENGTH_SHORT).show();
                                    }
                                    else
                                    {
                                        extResponse.setTextKeepState("Invalid REGISTER command.\n");
                                        extResponse.setTextKeepState("Use REGISTER optcode initlocal initremote acceptlocal acceptremote\n");
                                        extResponse.setTextKeepState("(optcode is an integer.)\n");
                                        extResponse.setTextKeepState("(initlocal, initremote, acceptlocal, acceptremote are boolean)\n");
                                    }
                                }
                            }
                            else if(line.startsWith("UNREGISTER"))
                            {
                                final StringTokenizer st = new StringTokenizer(new String(buff));
                                try
                                {
                                    st.nextToken();
                                    final int opcode = Integer.parseInt(st.nextToken());
                                    tc.deleteOptionHandler(opcode);
                                }
                                catch (final Exception e)
                                {
                                    if(e instanceof InvalidTelnetOptionException)
                                    {
                                        Toast.makeText(getApplicationContext(),"Error unregistering option: " + e.getMessage() + "\n",Toast.LENGTH_SHORT).show();
                                    }
                                    else
                                    {
                                        extResponse.setTextKeepState("Invalid UNREGISTER command.\n");
                                        extResponse.setTextKeepState("Use UNREGISTER optcode\n");
                                        extResponse.setTextKeepState("(optcode is an integer)\n");
                                    }
                                }
                            }
                            else if(line.startsWith("SPY"))
                            {
                                tc.registerSpyStream(fout);
                            }
                            else if(line.startsWith("UNSPY"))
                            {
                                tc.stopSpyStream();
                            }
                            else if(line.matches("^\\^[A-Z^]\\r?\\n?$"))
                            {
                                final byte toSend = buff[1];
                                if (toSend == '^') {
                                    outstr.write(toSend);
                                } else {
                                    outstr.write(toSend - 'A' + 1);
                                }
                                outstr.flush();
                            }
                            else
                            {
                                try
                                {
                                    outstr.write(buff, 0 , ret_read);
                                    outstr.flush();
                                }
                                catch (final IOException e)
                                {
                                    end_loop = true;
                                }
                            }
                        }
                    }
                    catch (final IOException e)
                    {
                        Toast.makeText(getApplicationContext(),"Exception while reading keyboard:" + e.getMessage(),Toast.LENGTH_SHORT).show();
                        end_loop = true;
                    }
                }
                while(ret_read > 0 && end_loop == false);

                try
                {
                    tc.disconnect();
                }
                catch (final IOException e)
                {
                    Toast.makeText(getApplicationContext(),"Exception while connecting:" + e.getMessage(),Toast.LENGTH_SHORT).show();
                }
            }
            catch (final IOException e)
            {
                Toast.makeText(getApplicationContext(),"Exception while connecting:" + e.getMessage(),Toast.LENGTH_SHORT).show();
                System.exit(1);
            }
        }
    }

    /**
     * Main for the TelnetClientExample.
     * @param //args input params
     * @throws Exception on error
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);




        hostnameText = (EditText)findViewById(R.id.editTextHostname);
        commandText = (EditText)findViewById(R.id.editTextCommand);

        Button btnConnect = (Button)findViewById(R.id.btnConnect);
        Button btnSend = (Button)findViewById(R.id.btnSendCommand);
        Button btnChromecast = (Button)findViewById(R.id.btnChromecast);
        Button btnPS4 = (Button)findViewById(R.id.btnPS4);
        Button  btnBluetooth = (Button)findViewById(R.id.btnBluetooth);
        Button btnSpotify = (Button)findViewById(R.id.btnSpotify);
        ToggleButton btnPower = (ToggleButton)findViewById(R.id.toggleButtonPower);
        SeekBar volumeSlider = (SeekBar)findViewById(R.id.seekBarVolume);
        extResponse = (TextView) findViewById(R.id.txtboxResponse);
        extResponse.setMovementMethod(new ScrollingMovementMethod());



        remoteip = "192.168.0.117";

        remoteport = 23;

        try
        {
            fout = new FileOutputStream ("spy.log", true);
        }
        catch (final IOException e)
        {
            Toast.makeText(getApplicationContext(),"Exception while opening the spy file: "
                    + e.getMessage(),Toast.LENGTH_SHORT).show();

        }

        tc = new TelnetClient();

        final TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("VT100", false, false, true, false);
        final EchoOptionHandler echoopt = new EchoOptionHandler(true, false, true, false);
        final SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(true, true, true, true);

        try
        {
            tc.addOptionHandler(ttopt);
            tc.addOptionHandler(echoopt);
            tc.addOptionHandler(gaopt);
        }
        catch (final InvalidTelnetOptionException | IOException e)
        {
            Toast.makeText(getApplicationContext(),"Error registering option handlers: " + e.getMessage(),Toast.LENGTH_SHORT).show();
        }


    }


     /*
     * Callback method called when TelnetClient receives an option
     * negotiation command.
     *
     * @param negotiation_code - type of negotiation command received
     * (RECEIVED_DO, RECEIVED_DONT, RECEIVED_WILL, RECEIVED_WONT, RECEIVED_COMMAND)
     * @param option_code - code of the option negotiated
     */
    @Override
    public void receivedNegotiation(final int negotiation_code, final int option_code)
    {
        String command = null;
        switch (negotiation_code) {
            case TelnetNotificationHandler.RECEIVED_DO:
                command = "DO";
                break;
            case TelnetNotificationHandler.RECEIVED_DONT:
                command = "DONT";
                break;
            case TelnetNotificationHandler.RECEIVED_WILL:
                command = "WILL";
                break;
            case TelnetNotificationHandler.RECEIVED_WONT:
                command = "WONT";
                break;
            case TelnetNotificationHandler.RECEIVED_COMMAND:
                command = "COMMAND";
                break;
            default:
                command = Integer.toString(negotiation_code); // Should not happen
                break;
        }
        extResponse.append("Received " + command + " for option code " + option_code);
    }



    /*
     * Reader thread.
     * Reads lines from the TelnetClient and echoes them
     * on the screen.
     */
    @Override
    public void run()
    {
        final InputStream instr = tc.getInputStream();

        try
        {
            final byte[] buff = new byte[1024];
            int ret_read = 0;

            do
            {
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                    extResponse.append(new String(buff, 0, ret_read));
                }
            }
            while (ret_read >= 0);
        }
        catch (final IOException e)
        {
            Toast.makeText(getApplicationContext(),"Exception while reading socket:" + e.getMessage(),Toast.LENGTH_SHORT).show();
        }

        try
        {
            tc.disconnect();
        }
        catch (final IOException e)
        {
            Toast.makeText(getApplicationContext(),"Exception while closing telnet:" + e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }
}