package model.server;

import model.client.ClientConfigs;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by Alexander on 07/06/2017.
 */
public class ServerConfigs {

    private static final String PORT_XML = "PORT_XML";
    private static final String PORT_OBJECTS = "PORT_OBJECTS";
    private static final String LOG_ON = "LOG_ON";
    private int portObjects;
    private int portXML;
    private Boolean logOn;

    public static final Logger log = LogManager.getLogger(ClientConfigs.class);

    public ServerConfigs() {}

    public ServerConfigs(String configFileName) {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(configFileName)) {
            properties.load(input);
            portObjects = Integer.parseInt(properties.getProperty(PORT_OBJECTS));
            portXML = Integer.parseInt(properties.getProperty(PORT_XML));
            logOn = Boolean.parseBoolean(properties.getProperty(LOG_ON));
            if (!logOn) {
                Configurator.setRootLevel(Level.OFF);
            }
        } catch (FileNotFoundException e) {
            log.error("Config file not found");
            System.exit(-1);
        } catch (IOException e) {
            log.error("I/O Exception : ", e);
            System.exit(-1);
        } catch (NumberFormatException e) {
            log.error("Could not parse config file : ", e);
            System.exit(-1);
        } catch (IllegalArgumentException e) {
            log.error("Config os incorrect");
            System.exit(-1);
        }
    }

    public void setPortObjects(int portObjects) {
        this.portObjects = portObjects;
    }

    public void setPortXML(int portXML) {
        this.portXML = portXML;
    }

    public void setLogOn(Boolean logOn) {
        this.logOn = logOn;
    }

    public int getPortObjects() {
        return portObjects;
    }

    public int getPortXML() {
        return portXML;
    }

    public Boolean getLogOn() {
        return logOn;
    }
}

