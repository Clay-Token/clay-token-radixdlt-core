/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.messages;

import java.util.Objects;

import org.radix.network.messaging.Message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("message.consensus.vertex")
public class VertexMessage extends Message {
	@JsonProperty("vertex")
	@DsonOutput(Output.ALL)
	private final Vertex vertex;

	VertexMessage() {
		// Serializer only
		super(0);
		this.vertex = null;
	}

	public VertexMessage(int magic, Vertex vertex) {
		super(magic);
		this.vertex = Objects.requireNonNull(vertex);
	}

	public Vertex vertex() {
		return this.vertex;
	}
}