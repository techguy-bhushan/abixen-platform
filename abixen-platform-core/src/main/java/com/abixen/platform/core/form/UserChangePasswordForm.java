/**
 * Copyright (c) 2010-present Abixen Systems. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.abixen.platform.core.form;

import com.abixen.platform.core.util.ModelKeys;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;


public class UserChangePasswordForm implements Form {

    @NotNull
    @NotEmpty
    @Length(max = ModelKeys.PASSWORD_MAX_LENGTH)
    private String currentPassword;

    @NotNull
    @NotEmpty
    @Length(max = ModelKeys.PASSWORD_MAX_LENGTH)
    private String newPassword;

    @NotNull
    @NotEmpty
    @Length(max = ModelKeys.PASSWORD_MAX_LENGTH)
    private String retypeNewPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getRetypeNewPassword() {
        return retypeNewPassword;
    }

    public void setRetypeNewPassword(String retypeNewPassword) {
        this.retypeNewPassword = retypeNewPassword;
    }
}
