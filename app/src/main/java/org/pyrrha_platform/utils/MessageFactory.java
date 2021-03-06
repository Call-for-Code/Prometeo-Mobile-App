/*******************************************************************************
 * Copyright (c) 2014-2016 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *    Mike Robertson - initial contribution
 *    Aldo Eisma - add bearing and speed to acceleration message
 *******************************************************************************/
package org.pyrrha_platform.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/**
 * Build messages to be published by the application.
 */
public class MessageFactory {

    /**
     * Construct a JSON formatted string text event message
     *
     * @param text String of text message to send
     * @return String containing JSON formatted message
     */
    public static String getTextMessage(String text) {
        return "{\"d\":{" +
                "\"text\":\"" + text + "\"" +
                " } }";
    }

    public static String getPyrrhaDeviceMessage(PyrrhaEvent pyrrhaEvent) {
        String msg = null;
        try {
            // convert user object to JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            JsonElement pyrrhaEventElement = gson.toJsonTree(pyrrhaEvent);
            //  pyrrhaEventElement.getAsJsonObject().addProperty("id", id);
            msg = gson.toJson(pyrrhaEventElement);

            // print JSON string
            System.out.println(msg);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return msg;
    }

}
