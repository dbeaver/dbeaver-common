/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package com.dbeaver.jdbc.model;

import java.sql.DriverPropertyInfo;

/**
 * Builder for {@link DriverPropertyInfo}
 */
public class DriverPropertyInfoBuilder {
    private String name;
    private String value;
    private boolean required;
    private String description;
    private String[] choices;

    public DriverPropertyInfoBuilder() {
    }

    /**
     * Create a new builder from an existing {@link DriverPropertyInfo}.
     *
     * @param info the existing {@link DriverPropertyInfo}.
     * @return the new builder.
     */
    public static DriverPropertyInfoBuilder from(DriverPropertyInfo info) {
        return new DriverPropertyInfoBuilder()
            .withName(info.name)
            .withValue(info.value)
            .isRequired(info.required)
            .withDescription(info.description)
            .withChoices(info.choices);
    }

    /**
     * Create a new builder from name and value.
     *
     * @return the new builder.
     */
    public static DriverPropertyInfoBuilder from(String name, String value) {
        return new DriverPropertyInfoBuilder()
            .withName(name)
            .withValue(value);
    }

    /**
     * Set the name of the property.
     *
     * @param name the name of the property.
     * @return this builder.
     */
    public DriverPropertyInfoBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the value of the property.
     *
     * @param value the value of the property.
     * @return this builder.
     */
    public DriverPropertyInfoBuilder withValue(String value) {
        this.value = value;
        return this;
    }

    /**
     * Set whether the property is required.
     *
     * @param required whether the property is required.
     * @return this builder.
     */
    public DriverPropertyInfoBuilder isRequired(boolean required) {
        this.required = required;
        return this;
    }

    /**
     * Set the description of the property.
     *
     * @param description the description of the property.
     * @return this builder.
     */
    public DriverPropertyInfoBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the choices of the property.
     *
     * @param choices the choices of the property.
     * @return this builder.
     */
    public DriverPropertyInfoBuilder withChoices(String[] choices) {
        this.choices = choices;
        return this;
    }

    /**
     * Build the {@link DriverPropertyInfo}.
     *
     * @return the {@link DriverPropertyInfo}.
     */
    public DriverPropertyInfo build() {
        DriverPropertyInfo info = new DriverPropertyInfo(name, value);
        info.required = this.required;
        info.description = this.description;
        info.choices = this.choices;
        return info;
    }
}
