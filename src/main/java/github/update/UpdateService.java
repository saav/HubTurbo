package github.update;

import static org.eclipse.egit.github.core.client.IGitHubConstants.CONTENT_TYPE_JSON;
import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_REPOS;
import github.GitHubClientExtended;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.client.GitHubRequest;
import org.eclipse.egit.github.core.client.NoSuchPageException;
import org.eclipse.egit.github.core.client.PageIterator;
import org.eclipse.egit.github.core.client.PagedRequest;
import org.eclipse.egit.github.core.service.GitHubService;

import util.Utility;

/**
 * Given a type of item and the current ETag, fetches a list of updated items.
 * Returns auxillary results in the form of an updated ETag and the time of response,
 * which may be retrieved via the provided getters.
 * Only provides the basic framework for fetching updates. Subclasses fill in the details.
 *
 * @param <T> The type of the entity that is updated
 * */
public class UpdateService<T> extends GitHubService {
    private static final Logger logger = LogManager.getLogger(UpdateService.class.getName());

    private final GitHubClientExtended client;
    private final String apiSuffix;
    private final String lastETags;

    // Auxillary results of calling getUpdatedItems
    private Optional<String> updatedETags = Optional.empty();
    private Date updatedCheckTime = new Date();

    // Cached results of calling getUpdatedItems
    private ArrayList<T> updatedItems = null;

    /**
     * @param client an authenticated GitHubClient
     * @param apiSuffix the API URI for the type of item; defined by subclasses
     * @param lastETag the last-known ETag for these items; may be null
     */
    public UpdateService(GitHubClientExtended client, String apiSuffix, String lastETags){
        assert client != null;
        assert apiSuffix != null && !apiSuffix.isEmpty();

        this.client = client;
        this.apiSuffix = apiSuffix;
        this.lastETags = lastETags;
    }

    /**
     * To be overridden by subclasses to specify additional information required by
     * the EGit API, such as the types of the results expected (for deserialisation
     * purposes). Should be called by overriding implementations.
     * Will be called by getUpdatedItems.
     * @param repoId the repository to make the request for
     * @return the request to make
     */
    protected PagedRequest<T> createUpdatedRequest(IRepositoryIdProvider repoId){
        PagedRequest<T> request = new PagedRequest<>();
        String path = SEGMENT_REPOS + "/" + repoId.generateId() + apiSuffix;
        request.setUri(path);
        request.setResponseContentType(CONTENT_TYPE_JSON);
        return request;
    }

    /**
     * Retrieves the requested items from GitHub. Sets the auxillary fields.
     * @param repoId the repository to get the items from
     * @return a list of requested items
     */
    public ArrayList<T> getUpdatedItems(IRepositoryIdProvider repoId){

        // Return cached results if available
        if (updatedItems != null)  {
            return updatedItems;
        }

        ArrayList<T> result = new ArrayList<>();

        String resourceDesc = repoId.generateId() + apiSuffix;

        logger.info(String.format("Updating %s with ETag %s", resourceDesc, lastETags));
        try {
            PagedRequest<T> request = createUpdatedRequest(repoId);
            Optional<ImmutablePair<List<String>, HttpURLConnection>> etags = getPagedEtags(request, client);

            if (!etags.isPresent()) {
                logger.warn(String.format("%s: error getting updated items", getClass().getSimpleName()));

                // Respond as if we succeeded and there were no updates.
                // The assumption is that updates are cheap and we can do them as frequently as needed.
            } else {
                updatedETags = combineETags(etags.get().getLeft());
                if (!updatedETags.isPresent() || updatedETags.get().equals(lastETags)){
                    logger.info("Nothing to update");
                } else {
                    result = new ArrayList<>(getPagedItems(resourceDesc,
                        new PageIterator<>(request, client)));
                    logger.info(String.format("New ETag for %s: %s", resourceDesc, updatedETags));
                }

                updateCheckTime(etags.get().getRight());
            }

        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
            return result;
        }

        updatedItems = result;
        return result;
    }

    /**
     * Combine ETags for multiple page into 1 string
     * @param etags
     * @return string of combined etags
     */
    private static Optional<String> combineETags(List<String> etags) {
        return Optional.of(Utility.join(etags, "#"));
    }

    /**
     * Gets a list of ETags for all pages returned from an API request and
     * also return the connection used to get the ETags so that their last check time
     * can be recorded elsewhere
     * @param request
     * @param client
     * @return a Optional list of ETags for all pages returned from an API request and
     *         corresponding HTTP connection or an empty Optional if an error occurs
     */
    private Optional<ImmutablePair<List<String>, HttpURLConnection>> getPagedEtags(
            GitHubRequest request, GitHubClientExtended client) {

        PageHeaderIterator iter = new PageHeaderIterator(request, client, "ETag");
        List<String> etags = new ArrayList<>();
        HttpURLConnection connection = null;

        while (iter.hasNext()) {
            try {
                etags.add(Utility.stripQuotes(iter.next()));
                if (connection == null) {
                    connection = iter.getLastConnection();
                }
            } catch (NoSuchPageException e) {
                logger.error("No such page exception at " + iter.getRequest().generateUri());
                return Optional.empty();
            }
        }

        return Optional.of(new ImmutablePair<>(etags, connection));
    }

    /**
     * A specialised version of GitHubService::getPage that does logging.
     * @param iterator the paged request to iterate through
     * @return a list of items
     * @throws IOException
     */
    private List<T> getPagedItems(String resourceDesc, PageIterator<T> iterator) throws IOException {
        List<T> elements = new ArrayList<>();
        int length = 0;
        int page = 0;
        try {
            while (iterator.hasNext()) {
                elements.addAll(iterator.next());
                int diff = elements.size() - length;
                length = elements.size();
                logger.info(resourceDesc + " | page " + (page++) + ": " + diff + " items");
            }
        } catch (NoSuchPageException pageException) {
            throw pageException.getCause();
        }
        return elements;
    }

    /**
     * Returns the ETag for the updated items.
     * In the event of failure, will be whatever the last provided ETag was.
     * @return ETag for updated items
     */
    public String getUpdatedETags() {
        if (updatedETags.isPresent()) {
            return updatedETags.get();
        } else {
            return lastETags;
        }
    }

    /**
     * Returns the time at which the updated items were sent from the server.
     * In the event of failure, will be the time the request was made.
     * @return time at which updated items were sent from server
     */
    public Date getUpdatedCheckTime() {
        return new Date(updatedCheckTime.getTime());
    }

    private void updateCheckTime(HttpURLConnection connection) {
        String date = connection.getHeaderField("Date");
        updatedCheckTime = Utility.parseHTTPLastModifiedDate(date);
    }
}
