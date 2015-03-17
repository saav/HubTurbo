package service.updateservice;

import static org.eclipse.egit.github.core.client.IGitHubConstants.CONTENT_TYPE_JSON;
import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_ISSUES;
import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_REPOS;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.client.GitHubRequest;
import org.eclipse.egit.github.core.client.PageIterator;
import org.eclipse.egit.github.core.client.PagedRequest;
import org.eclipse.egit.github.core.service.GitHubService;

import service.GitHubClientExtended;


/**
 * Base class for obtaining updates from Github for a repository object
 * */
public class UpdateService<T> extends GitHubService{
	private static final Logger logger = LogManager.getLogger(UpdateService.class.getName());
	private final String apiSuffix;
	protected GitHubClientExtended client;
	private String lastETag;
	protected Date lastCheckTime;
	protected String lastIssueCheckTime;

	public UpdateService(GitHubClientExtended client, String apiSuffix){
		this.client = client;
		this.apiSuffix = apiSuffix;
	}

	private void updateLastETag(HttpURLConnection connection){
		lastETag = connection.getHeaderField("ETag");
	}

	protected void setLastETag(String ETag) {
		this.lastETag = ETag;
	}

	public String getLastETag() {
		return this.lastETag;
	}

	protected void setLastIssueCheckTime(String date) {
		this.lastIssueCheckTime = date;
	}

	public String getLastIssueCheckTime() {
		return this.lastIssueCheckTime;
	}

	protected String getFormattedDate(Date date){
		TimeZone tz = TimeZone.getTimeZone("UTC");
	    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
	    df.setTimeZone(tz);
	    String formatted = df.format(date);
	    return formatted;
	}

	private void updateLastCheckTime(HttpURLConnection connection) throws ParseException{
		String date = connection.getHeaderField("Date");
		lastCheckTime = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").parse(date);
	}

	private HttpURLConnection createUpdatedConnection(GitHubRequest request) throws IOException{
		HttpURLConnection connection = client.createConnection(request);
		if(lastETag != null){
			connection.setRequestProperty("If-None-Match", lastETag);
		}
		return connection;
	}

	protected PagedRequest<T> createUpdatedRequest(IRepositoryIdProvider repoId){
		PagedRequest<T> request = new PagedRequest<T>();
		String path = SEGMENT_REPOS + "/" + repoId.generateId() + apiSuffix;
		request.setUri(path);
		request.setResponseContentType(CONTENT_TYPE_JSON);
		return request;
	}
	
	public ArrayList<T> getUpdatedItems(IRepositoryIdProvider repoId){
		ArrayList<T> result = new ArrayList<T>();
		try {

			PagedRequest<T> request = createUpdatedRequest(repoId);
			PageIterator<T> requestIterator = new PageIterator<T>(request, client);
			HttpURLConnection connection = createUpdatedConnection(request);
			int responseCode = connection.getResponseCode();

			if(client.isError(responseCode)){
				return new ArrayList<T>();
			}

			if(responseCode != GitHubClientExtended.NO_UPDATE_RESPONSE_CODE){
				result = (ArrayList<T>)getAll(requestIterator);
			}
			updateLastETag(connection);
			updateLastCheckTime(connection);
		} catch (IOException e) {
			if(!(e instanceof UnknownHostException || e instanceof SocketTimeoutException)){
				logger.error(e.getLocalizedMessage(), e);
			}
		} catch (ParseException e) {
			//should not happen
			logger.error(e.getLocalizedMessage(), e);
		}
		return result;
	}

}
