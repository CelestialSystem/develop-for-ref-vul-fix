package com.filestack;

import com.filestack.internal.BaseService;
import com.filestack.internal.CdnService;
import com.filestack.internal.Networking;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Okio;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.mock.Calls;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link FileLink FileLink} class.
 */
public class TestFileLink {

  private CdnService cdnService = mock(CdnService.class);
  private BaseService baseService = mock(BaseService.class);

  /** Set networking singletons to mocks. */
  @Before
  public void setup() {
    Networking.setCdnService(cdnService);
    Networking.setBaseService(baseService);
  }

  @Test
  public void testGetContent() throws Exception {
    when(cdnService.get("handle", null, null))
            .thenReturn(Helpers.createRawCall("text/plain", "Test content"));

    Config config = new Config("apikey");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");

    ResponseBody content = fileLink.getContent();
    Assert.assertEquals("Test content", content.string());
  }

  @Test
  public void testDownload() throws Exception {
    when(cdnService.get("handle", null, null))
            .thenReturn(Helpers.createRawCall("text/plain", "Test content"));

    Config config = new Config("apikey");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");

    File file = fileLink.download("/tmp/");
    Assert.assertTrue(file.isFile());
    if (!file.delete()) {
      Assert.fail("Unable to cleanup resource");
    }
  }

  @Test
  public void testDownloadCustomFilename() throws Exception {
    when(cdnService.get("handle", null, null))
            .thenReturn(Helpers.createRawCall("text/plain", "Test content"));

    Config config = new Config("apikey");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");

    File file = fileLink.download("/tmp/", "filestack_test_filelink_download.txt");
    Assert.assertTrue(file.isFile());
    if (!file.delete()) {
      Assert.fail("Unable to cleanup resource");
    }
  }

  @Test
  public void testOverwrite() throws Exception {
    File file = File.createTempFile("filestack", ".txt");
    file.deleteOnExit();

    Okio.buffer(Okio.sink(file)).writeUtf8("Test content").close();

    when(baseService.overwrite(anyString(), anyString(), anyString(), any(RequestBody.class)))
            .thenReturn(Helpers.createRawCall("application/json", ""));

    Config config = new Config("apiKey", "policy", "signature");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");

    fileLink.overwrite(file.getAbsolutePath());
  }

  @Test
  public void testDelete() throws Exception {
    when(baseService.delete("handle", "apiKey", "policy", "signature"))
            .thenReturn(Helpers.createRawCall("application/json", ""));

    Config config = new Config("apiKey", "policy", "signature");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");

    fileLink.delete();
  }

  @Test(expected = IllegalStateException.class)
  public void testOverwriteWithoutSecurity() throws Exception {
    Config config = new Config("apiKey");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");
    fileLink.overwrite("");
  }

  @Test(expected = FileNotFoundException.class)
  public void testOverwriteNoFile() throws Exception {
    Config config = new Config("apiKey", "policy", "signature");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");
    fileLink.overwrite("/tmp/filestack_test_overwrite_no_file.txt");
  }

  @Test(expected = IllegalStateException.class)
  public void testDeleteWithoutSecurity() throws Exception {
    Config config = new Config("apiKey");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");
    fileLink.delete();
  }

  @Test(expected = IllegalStateException.class)
  public void testImageTagNoSecurity() throws Exception {
    Config config = new Config("apiKey");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");
    fileLink.imageTags();
  }

  @Test
  public void testImageTag() throws Exception {
    String jsonString = "{"
        + "'tags': {"
        + "'auto': {"
        + "'giraffe': 100"
        + "},"
        + "'user': null"
        + "}"
        + "}";

    String tasksString = "security=policy:policy,signature:signature/tags";

    when(cdnService.transform(tasksString, "handle"))
            .thenReturn(Helpers.createRawCall("application/json", jsonString));

    Config config = new Config("apiKey", "policy", "signature");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");

    Map<String, Integer> tags = fileLink.imageTags();

    Assert.assertEquals((Integer) 100, tags.get("giraffe"));
  }

  @Test(expected = IllegalStateException.class)
  public void testImageSfwNoSecurity() throws Exception {
    Config config = new Config("apiKey");
    FileLink fileLink = new FileLink(config, cdnService, baseService, "handle");
    fileLink.imageSfw();
  }

  @Test
  public void testImageSfw() throws Exception {
    when(cdnService.transform(anyString(), anyString()))
            .thenAnswer(new Answer() {
              @Override
              public Call<ResponseBody> answer(InvocationOnMock invocation) {
                String handle = invocation.getArgument(1);
                String json = "{'sfw': " + (handle.equals("safe") ? "true" : "false") + "}";
                MediaType mediaType = MediaType.parse("application/json");
                return Calls.response(ResponseBody.create(mediaType, json));
              }
            });


    Config config = new Config("apiKey", "policy", "signature");

    FileLink safe = new FileLink(config, "safe");
    FileLink notSafe = new FileLink(config, "notSafe");

    Assert.assertTrue(safe.imageSfw());
    Assert.assertFalse(notSafe.imageSfw());
  }
}
