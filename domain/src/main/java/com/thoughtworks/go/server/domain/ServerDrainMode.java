/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.domain;

import java.sql.Timestamp;
import java.util.Date;

public class ServerDrainMode {
    private boolean isDrained = false;
    private String updatedBy;
    private Timestamp updatedOn;

    public ServerDrainMode() {
    }

    public ServerDrainMode(boolean isDrained, String updatedBy, Date updatedOn) {
        this.isDrained = isDrained;
        this.updatedBy = updatedBy;
        this.updatedOn = new Timestamp(updatedOn.getTime());
    }

    public boolean isDrainMode() {
        return isDrained;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public Timestamp updatedOn() {
        return updatedOn;
    }

    public void updatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void updatedOn(Timestamp updatedOn) {
        this.updatedOn = updatedOn;
    }

    public void setDrainMode(boolean drained) {
        isDrained = drained;
    }
}
