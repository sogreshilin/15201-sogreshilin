package model.message;

import model.client.MessageHandler;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

/**
 * Created by Alexander on 03/06/2017.
 */
@XmlRootElement(name = "event")
@XmlType(propOrder = {"message", "name"})
public class UserMessage implements ServerMessage, DisplayMessage, Serializable {
    @XmlAttribute(name = "name")
    private final String messageType = "message";
    private String name;
    private String message;

    public UserMessage() {
    }

    @XmlElement(name = "name")
    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "message")
    public void setMessage(String message) {
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void process(MessageHandler handler) {
        handler.process(this);
    }

    @Override
    public String messageToShow() {
        return message;
    }
}
