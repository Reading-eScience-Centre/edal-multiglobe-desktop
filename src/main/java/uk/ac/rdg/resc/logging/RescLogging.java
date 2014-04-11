/*******************************************************************************
 * Copyright (c) 2014 The University of Reading
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package uk.ac.rdg.resc.logging;

import gov.nasa.worldwind.util.Logging;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;

/**
 * Provides methods for accessing error messages related to ReSC classes (i.e.
 * video wall specific ones). This code is copied from
 * gov.nasa.worldwind.util.Logging with the resource file location changed. ReSC
 * classes should use this class to get the appropriate messages for logging.
 * This allows for multiple language support in exception messages.
 * 
 * @author Guy Griffiths
 */
public class RescLogging {
    protected static final String MESSAGE_BUNDLE_NAME = RescLogging.class.getPackage().getName()
            + ".RescMessageStrings";

    /**
     * Retrieves a message from the Resc World Wind message resource bundle.
     * 
     * @param property
     *            the property identifying which message to retrieve.
     * 
     * @return The requested message.
     */
    public static String getMessage(String property) {
        try {
            return (String) ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME, Locale.getDefault())
                    .getObject(property);
        } catch (Exception e) {
            String message = "Exception looking up message from bundle " + MESSAGE_BUNDLE_NAME;
            Logging.logger().log(java.util.logging.Level.SEVERE, message, e);
            return message;
        }
    }

    /**
     * Retrieves a message from the Resc World Wind message resource bundle formatted
     * with a single argument. The argument is inserted into the message via
     * {@link java.text.MessageFormat}.
     * 
     * @param property
     *            the property identifying which message to retrieve.
     * @param arg
     *            the single argument referenced by the format string identified
     *            <code>property</code>.
     * 
     * @return The requested string formatted with the argument.
     * 
     * @see java.text.MessageFormat
     */
    public static String getMessage(String property, String arg) {
        return arg != null ? getMessage(property, (Object) arg) : getMessage(property);
    }

    /**
     * Retrieves a message from the Resc World Wind message resource bundle formatted
     * with specified arguments. The arguments are inserted into the message via
     * {@link java.text.MessageFormat}.
     * 
     * @param property
     *            the property identifying which message to retrieve.
     * @param args
     *            the arguments referenced by the format string identified
     *            <code>property</code>.
     * 
     * @return The requested string formatted with the arguments.
     * 
     * @see java.text.MessageFormat
     */
    public static String getMessage(String property, Object... args) {
        String message;

        try {
            message = (String) ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME, Locale.getDefault())
                    .getObject(property);
        } catch (Exception e) {
            message = "Exception looking up message from bundle " + MESSAGE_BUNDLE_NAME;
            Logging.logger().log(Level.SEVERE, message, e);
            return message;
        }

        try {
            return args == null ? message : MessageFormat.format(message, args);
        } catch (IllegalArgumentException e) {
            message = "Message arguments do not match format string: " + property;
            Logging.logger().log(Level.SEVERE, message, e);
            return message;
        }
    }
}