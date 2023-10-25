package com.github.gekoh.yagen.ddl;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;

public class EntityClassesSaxHandler extends DefaultHandler {
    private final Collection<Class> entityClasses;
    private SAXParserFactory factory;
    private boolean inMappingFile = false;
    private boolean inClass = false;

    public EntityClassesSaxHandler(Collection<Class> entityClasses) {
        this.entityClasses = entityClasses;
    }

    private EntityClassesSaxHandler(SAXParserFactory factory, Collection<Class> entityClasses) {
        this.factory = factory;
        this.entityClasses = entityClasses;
    }

    public void parseXmlFileForEntityClasses(String persistenceOrOrmXml) {
        try {
            if (factory == null) {
                factory = SAXParserFactory.newInstance();
            }
            InputStream resource = EntityClassesSaxHandler.class.getResourceAsStream("/" + persistenceOrOrmXml);
            if (resource == null) {
                resource = new FileInputStream(persistenceOrOrmXml);
            }

            SAXParser parser = factory.newSAXParser();
            parser.parse(resource, this);
            resource.close();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to find or parse resource: "+ persistenceOrOrmXml + " in classpath or filesystem.", ex);
        }

    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (qName) {
            case "mapping-file":
                inMappingFile = true;
                break;
            case "class":
                inClass = true;
                break;
            case "mapped-superclass":
            case "entity":
                addEntityClass(attributes.getValue("class"));
                break;
            default:
        }
    }

    private void addEntityClass(String className) {
        try {
            entityClasses.add(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Entity class not found!", e);
        }
    }


    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (qName) {
            case "mapping-file": inMappingFile = false; break;
            case "class": inClass = false; break;
            default:
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inClass) {
            addEntityClass(new String(ch, start, length));
        } else if (inMappingFile) {
            String mappingFile = new String(ch, start, length);
            // new handler instance for recursive parsing!
            new EntityClassesSaxHandler(factory, entityClasses).parseXmlFileForEntityClasses(mappingFile);
        }
    }

}