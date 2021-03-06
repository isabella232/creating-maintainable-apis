package rs.exproit.load_profile_generator;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.ipc.NettyTransceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.util.Utf8;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rs.exproit.load_profile_generator.domain.*;
import rs.exproit.load_profile_generator.protocol.LPCreationRequest;
import rs.exproit.load_profile_generator.protocol.LoadProfileRPC;

public class ClientServerTest {
    private static final String TEST_DATA_FILE ="load_profiles_test.avro";    
    private static final int PORT = 65111;
    
    private App app;
    private NettyTransceiver client;

    @Before
    public void createClient() throws IOException {
        app = new App(new LoadProfileRPCImpl(TEST_DATA_FILE));
        app.start(PORT);
        client = new NettyTransceiver(new InetSocketAddress(PORT));
    }
    
    @After
    public void closeClient() {
        client.close();
        app.stop();
        new File(TEST_DATA_FILE).delete();
    }

    private LPCreationRequest createRequest(String loadCondition) {
        LPCreationRequest request = LPCreationRequest.newBuilder().build();        
        if (loadCondition != null) {
            request.setLoadCondition(loadCondition);
        }
        return request;
    }
    
    @Test
    public void triggerLPCreationsAndValidateThatDataIsSaved() throws IOException {
        LoadProfileRPC proxy = SpecificRequestor.getClient(LoadProfileRPC.class, client);
        
        String response;
        for (int i = 0; i < 100; i++) {
            response = 
                    proxy
                     .lpCreate(createRequest("http://example.org/api/Controller/LoadConditions/" + i))
                     .toString();
            assertNotNull(response);
            assert(response).contains(
                    "\"loadCondition\": \"http://example.org/api/Controller/LoadConditions/" + i + "\"");
        }
        response = proxy.lpCreate(createRequest(null)).toString();
        assertNotNull(response);
        assert(response).contains("\"loadCondition\": null");

        // Read and verify the serialized data file.
        DatumReader<LoadProfileModel> lpDatumReader = new SpecificDatumReader<LoadProfileModel>(LoadProfileModel.class);
        DataFileReader<LoadProfileModel> dataFileReader = new DataFileReader<LoadProfileModel>(new File(TEST_DATA_FILE), lpDatumReader);
        LoadProfileModel load_profile = null;
        for (int i = 0; i < 100; i++) {
            // Reuse load profile object and avoid excessive garbage collections.
            load_profile = dataFileReader.next(load_profile);
            assertEquals(new Utf8("http://example.org/api/Controller/LoadConditions/" + i), load_profile.getLoadCondition());
        }
    }
}
