package com.radixdlt.atommodel.tokens;

import java.util.Map;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.api.AtomSchemas;
import org.radix.modules.Modules;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Atom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TokenDefinitionSchemaTest {

	static Serialization serialization;

	@BeforeClass
	public static void setupSerializer() {
		serialization = Serialization.create(ClasspathScanningSerializerIds.create(), ClasspathScanningSerializationPolicy.create());
		Modules.replace(Serialization.class, serialization);
	}

	@AfterClass
	public static void teardownSerializer() {
		Modules.remove(Serialization.class);
	}

	@Test
	public void when_validating_complete_tokendefinition_particle_against_schema__validation_is_successful() throws CryptoException {
		ECKeyPair kp = new ECKeyPair();
		RadixAddress addr = new RadixAddress((byte) 12, kp.getPublicKey());
		Map<TokenTransition, TokenPermission> tp = ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
			TokenTransition.BURN, TokenPermission.TOKEN_CREATION_ONLY
		);
		TokenDefinitionParticle tokenDefinition = new TokenDefinitionParticle(addr, "TEST", "Test token", "Test token", UInt256.ONE, "http://example.com", tp);
		Atom atom = new Atom();
		atom.addParticleGroupWith(tokenDefinition, Spin.UP);

		JSONObject jsonAtom = serialization.toJsonObject(atom, Output.WIRE);

		Schema schema = AtomSchemas.get();
		schema.validate(jsonAtom);

		// All good if we get here - exception on validation fail
		assertTrue(true);
	}

	@Test(expected = ValidationException.class)
	public void when_validating_old_tokendefinition_particle_against_schema__an_exception_is_thrown() {
		String strAtom = "" +
			"{\n" +
			"    \"serializer\": \"radix.atom\",\n" +
			"    \"version\": 100,\n" +
			"    \"particleGroups\": [{\n" +
			"        \"serializer\": \"radix.particle_group\",\n" +
			"        \"particles\": [{\n" +
			"            \"spin\": 1,\n" +
			"            \"serializer\": \"radix.spun_particle\",\n" +
			"            \"particle\": {\n" +
			"                \"symbol\": \":str:TEST\",\n" +
			"                \"address\": \":adr:2n3VYbjQyB2sySwMqiacjGjzAwEHYrgnSsZXDBo1F2x49a3RMBsF\",\n" +
			"                \"granularity\": \":u20:1\",\n" +
			"                \"permissions\": {\n" +
			"                    \"burn\": \":str:token_creation_only\",\n" +
			"                    \"mint\": \":str:token_creation_only\"\n" +
			"                },\n" +
			"                \"destinations\": [\":uid:1c4c0a2915f4406ddfbd6c64549cfe1a\"],\n" +
			"                \"name\": \":str:Test token\",\n" +
			"                \"serializer\": \"radix.particles.token_definition\",\n" +
			"                \"description\": \":str:Test token\",\n" +
			"                \"icon\": \":byt:some bytes that don't matter for this test\",\n" +
			"                \"version\": 100\n" +
			"            },\n" +
			"            \"version\": 100\n" +
			"        }],\n" +
			"        \"version\": 100\n" +
			"    }]\n" +
			"}";
		JSONObject jsonAtom = new JSONObject(strAtom);
		Schema schema = AtomSchemas.get();
		schema.validate(jsonAtom);

		// fail if we get here - exception should be thrown
		fail();
	}
}