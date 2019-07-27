/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package de.gliderpilot.comportpipe;

import static jssc.SerialPort.BAUDRATE_115200;
import static jssc.SerialPort.DATABITS_8;
import static jssc.SerialPort.PARITY_NONE;
import static jssc.SerialPort.STOPBITS_1;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import jssc.SerialPort;
import jssc.SerialPortException;

public class App
{

    public static void main(String[] args)
    {
        if (args.length < 2)
        {
            System.out.println("Usage: ComPortPipe input-devicename(s) output devicename");
            System.exit(1);
        }
        List<SaveSerialPort> inputPorts =
            Arrays.stream(args).limit(args.length - 1).map(SaveSerialPort::new).collect(Collectors.toList());
        SaveSerialPort outputPort = new SaveSerialPort(args[args.length - 1]);
        ExecutorService executorService = Executors.newFixedThreadPool(inputPorts.size());
        for (SaveSerialPort port : inputPorts)
        {
            executorService.execute(new PipePorts(port, outputPort));
        }
        /*
        SwingUtilities.invokeLater(() ->
        {
            TrayIcon trayIcon = new TrayIcon(null);
        });
        */
    }

    private static class PipePorts implements Runnable
    {
        private final SaveSerialPort inputPort;
        private final SaveSerialPort outputPort;

        public PipePorts(SaveSerialPort inputPort, SaveSerialPort outputPort)
        {

            this.inputPort = inputPort;
            this.outputPort = outputPort;
        }

        @Override
        public void run()
        {
            while (true)
            {
                try
                {
                    SerialPort port = inputPort.getPort();
                    if (port != null)
                    {
                        port.addEventListener(e ->
                        {
                            byte[] bytes = inputPort.read();
                            if (bytes != null && bytes.length > 0)
                            {
                                outputPort.write(bytes);
                            }
                        });
                    }
                    // wait till the port has changed, check every second
                    while (inputPort.getPort() == port)
                    {
                        Thread.sleep(1000);
                    }
                }
                catch (Exception e)
                {
                    // ignore
                    e.printStackTrace();
                }
            }
        }
    }

    private static class SaveSerialPort
    {
        private final String portName;
        private SerialPort port;

        public SaveSerialPort(String portName)
        {
            this.portName = portName;
            try
            {
                checkPort();
            }
            catch (SerialPortException e)
            {
                System.out.println(portName + " is not available (yet?)");
            }
        }

        public synchronized byte[] read()
        {
            try
            {
                checkPort();
                byte[] bytes = port.readBytes();
                if (bytes != null && bytes.length > 0)
                {
                    System.out.println("received " + bytes.length + " bytes from " + portName);
                }
                return bytes;
            }
            catch (Exception e)
            {
                closePort();
            }
            return null;
        }

        public synchronized void write(byte[] bytes)
        {
            try
            {
                checkPort();
                port.writeBytes(bytes);
                System.out.println("wrote " + bytes.length + " bytes to " + portName);
            }
            catch (Exception e)
            {
                closePort();
            }
        }

        public SerialPort getPort()
        {
            try
            {
                checkPort();
            }
            catch (SerialPortException e)
            {
                // ignore
            }
            return port;
        }

        private void checkPort() throws SerialPortException
        {
            if (port == null)
            {
                openPort();
                System.out.println("connected to " + portName);
            }
            // check if port is closed
            if (port.getInputBufferBytesCount() < 0)
            {
                System.out.println("lost connection to " + portName);
                closePort();
                checkPort();
            }
        }

        private void openPort() throws SerialPortException
        {
            SerialPort port = new SerialPort(portName);
            port.openPort();
            port.setParams(BAUDRATE_115200, DATABITS_8, STOPBITS_1, PARITY_NONE);
            this.port = port;
        }

        private void closePort()
        {
            if (port != null)
            {
                removePortListener();
                try
                {
                    port.closePort();
                    System.out.println("closed " + portName);
                }
                catch (Exception e)
                {
                    System.out.println("could not close " + portName + ": " + e.getMessage());
                }
                finally
                {
                    port = null;
                }
            }
        }

        private void removePortListener()
        {
            try
            {
                port.removeEventListener();
            }
            catch (Exception e)
            {
                // If the port does not exist any more, removeEventListener() will not work, but closePort
                // will neither, because it checks for eventListenerAdded. We must change the value
                // manually
                try
                {
                    Field eventListenerAddedField = SerialPort.class.getDeclaredField("eventListenerAdded");
                    eventListenerAddedField.setAccessible(true);
                    eventListenerAddedField.set(port, false);
                }
                catch (Exception inner)
                {
                    inner.printStackTrace();
                }
            }
        }
    }
}
