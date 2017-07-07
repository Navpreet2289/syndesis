/**
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.model.integration;

import java.io.Serializable;
import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.syndesis.integration.model.StepKinds;
import io.syndesis.model.Kind;
import io.syndesis.model.WithId;
import io.syndesis.model.WithName;

import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = IntegrationPattern.Builder.class)
public interface IntegrationPattern extends WithId<IntegrationPattern>, WithName, Serializable {

    IntegrationPattern CHOICE = new IntegrationPattern.Builder().id(StepKinds.OTHERWISE).name("Choice").build();
    IntegrationPattern OTHERWISE = new IntegrationPattern.Builder().id(StepKinds.OTHERWISE).name("Otherwise").build();
    IntegrationPattern MESSAGE_FILTER = new IntegrationPattern.Builder().id(StepKinds.FILTER).name("Message Filter").build();
    IntegrationPattern SPLITTER = new IntegrationPattern.Builder().id(StepKinds.SPLIT).name("Splitter").build();
    IntegrationPattern THROTTLER = new IntegrationPattern.Builder().id(StepKinds.THROTTLE).name("Throttler").build();

    IntegrationPattern SET_BODY = new IntegrationPattern.Builder().id(StepKinds.SET_BODY).name("Set Body").build();
    IntegrationPattern SET_HEDER = new IntegrationPattern.Builder().id(StepKinds.SET_HEADERS).name("Set Headers").build();

    @Override
    default Kind getKind() {
        return Kind.IntegrationPattern;
    }

    String getIcon();

    String getProperties();

    Optional<IntegrationPatternGroup> getIntegrationPatternGroup();

    Optional<String> getIntegrationPatternGroupId();

    @Override
    default IntegrationPattern withId(String id) {
        return new Builder().createFrom(this).id(id).build();
    }

    class Builder extends ImmutableIntegrationPattern.Builder {
    }

}
