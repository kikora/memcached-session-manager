package de.javakaffee.web.msm;

import de.javakaffee.web.msm.LockingStrategy.LockingMode;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Response;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.util.SessionConfig;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import static de.javakaffee.web.msm.Statistics.StatsType.*;

public class MemcachedBackupSessionManager extends ManagerBase implements Lifecycle, MemcachedSessionService.SessionManager
{

    protected static final String NAME = MemcachedBackupSessionManager.class.getSimpleName();
    protected final Log _log = LogFactory.getLog( getClass() );

    protected MemcachedSessionService _msm;
    private Integer _maxInactiveInterval;
    private Boolean _contextHasFormBasedSecurityConstraint;

    public MemcachedBackupSessionManager() {
        _msm = new MemcachedSessionService( this ) {
            @Override
            protected RequestTrackingContextValve createRequestTrackingContextValve(final String sessionCookieName) {
                final RequestTrackingContextValve result = super.createRequestTrackingContextValve(sessionCookieName);
                result.setAsyncSupported(true);
                return result;
            }
            @Override
            protected RequestTrackingHostValve createRequestTrackingHostValve(final String sessionCookieName, final CurrentRequest currentRequest) {
                final RequestTrackingHostValve result = super.createRequestTrackingHostValve(sessionCookieName, currentRequest);
                result.setAsyncSupported(true);
                return result;
            }
        };
    }

    @Override
    public String getName() {
        return NAME;
    }


    @Override
    public void load() throws ClassNotFoundException, IOException {

    }

    @Override
    public void unload() throws IOException {

    }

    @Override
    public MemcachedBackupSession createSession( final String sessionId ) {
        return _msm.createSession(sessionId);
    }

    @Override
    public MemcachedBackupSession createEmptySession() {
        return _msm.createEmptySession();
    }

    @Override
    public MemcachedBackupSession newMemcachedBackupSession() {
        return new MemcachedBackupSession( this );
    }

    @Override
    public String generateSessionId() {
        return _msm.newSessionId( super.generateSessionId() );
    }

    @Override
    public void expireSession( final String sessionId ) {
        if ( _log.isDebugEnabled() ) {
            _log.debug( "expireSession invoked: " + sessionId );
        }
        super.expireSession( sessionId );
        _msm.deleteFromMemcached(sessionId);
    }

    @Override
    public void remove( final Session session, final boolean update ) {
        removeInternal(session, update, session.getNote(MemcachedSessionService.NODE_FAILURE) != Boolean.TRUE);
    }

    @Override
    public void removeInternal( final Session session, final boolean update ) {
        super.remove(session, update);
    }

    private void removeInternal( final Session session, final boolean update, final boolean removeFromMemcached ) {
        if ( _log.isDebugEnabled() ) {
            _log.debug( "remove invoked, removeFromMemcached: " + removeFromMemcached +
                    ", id: " + session.getId() );
        }
        if ( removeFromMemcached ) {
            _msm.deleteFromMemcached( session.getId() );
        }
        super.remove( session, update );
        _msm.sessionRemoved((MemcachedBackupSession) session);
    }

    @Override
    public Session findSession( final String id ) throws IOException {
        return _msm.findSession( id );
    }

    @Override
    public boolean isMaxInactiveIntervalSet() {
        return _maxInactiveInterval != null;
    }

    @Override
    public int getMaxInactiveInterval() {
        return _maxInactiveInterval;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        Integer oldMaxInactiveInterval = _maxInactiveInterval;
        _maxInactiveInterval = interval;
        support.firePropertyChange("maxInactiveInterval",
                oldMaxInactiveInterval,
                _maxInactiveInterval);
    }

    @Override
    public void setMemcachedNodes( final String memcachedNodes ) {
        _msm.setMemcachedNodes( memcachedNodes );
    }

    public String getMemcachedNodes() {
        return _msm.getMemcachedNodes();
    }

    @Override
    public void setFailoverNodes( final String failoverNodes ) {
        _msm.setFailoverNodes( failoverNodes );
    }

    public String getFailoverNodes() {
        return _msm.getFailoverNodes();
    }

    public void setRequestUriIgnorePattern( final String requestUriIgnorePattern ) {
        _msm.setRequestUriIgnorePattern( requestUriIgnorePattern );
    }

    Pattern getSessionAttributePattern() {
        return _msm.getSessionAttributePattern();
    }

    public String getSessionAttributeFilter() {
        return _msm.getSessionAttributeFilter();
    }

    public void setSessionAttributeFilter(  final String sessionAttributeFilter ) {
        _msm.setSessionAttributeFilter(sessionAttributeFilter);
    }

    public void setTranscoderFactoryClass( final String transcoderFactoryClassName ) {
        _msm.setTranscoderFactoryClass(transcoderFactoryClassName);
    }

    public void setCopyCollectionsForSerialization( final boolean copyCollectionsForSerialization ) {
        _msm.setCopyCollectionsForSerialization(copyCollectionsForSerialization);
    }

    public void setCustomConverter( final String customConverterClassNames ) {
        _msm.setCustomConverter(customConverterClassNames);
    }

    public void setEnableStatistics( final boolean enableStatistics ) {
        _msm.setEnableStatistics(enableStatistics);
    }

    public void setBackupThreadCount( final int backupThreadCount ) {
        _msm.setBackupThreadCount( backupThreadCount );
    }

    public int getBackupThreadCount() {
        return _msm.getBackupThreadCount();
    }

    public void setMemcachedProtocol( final String memcachedProtocol ) {
        _msm.setMemcachedProtocol(memcachedProtocol);
    }

    @Override
    public void setEnabled( final boolean enabled ) throws IllegalStateException {
        _msm.setEnabled( enabled );
    }

    public boolean isEnabled() {
        return _msm.isEnabled();
    }

    @Override
    public void setSticky( final boolean sticky ) {
        _msm.setSticky( sticky );
    }

    public boolean isSticky() {
        return _msm.isSticky();
    }

    @Override
    public void setOperationTimeout(final long operationTimeout ) {
        _msm.setOperationTimeout(operationTimeout);
    }

    public long getOperationTimeout() {
        return _msm.getOperationTimeout();
    }

    @Override
    public void setLockExpiration(int lockExpiration) {
        _msm.setLockExpiration(lockExpiration);
    }

    @Override
    public void setLockingMode( final String lockingMode ) {
        _msm.setLockingMode( lockingMode );
    }

    @Override
    public void setLockingMode( final LockingMode lockingMode, final Pattern uriPattern, final boolean storeSecondaryBackup ) {
        _msm.setLockingMode(lockingMode, uriPattern, storeSecondaryBackup);
    }

    @Override
    public void setUsername(final String username) {
        _msm.setUsername(username);
    }

    @Override
    public void setPassword(final String password) {
        _msm.setPassword(password);
    }

    public void setStorageKeyPrefix(final String storageKeyPrefix) {
        _msm.setStorageKeyPrefix(storageKeyPrefix);
    }

    @Override
    public void startInternal() throws LifecycleException {
        super.startInternal();
        _msm.startInternal();
        setState(LifecycleState.STARTING);
    }

    @Override
    public void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);

        if ( _msm.isSticky() ) {
            _log.info( "Removing sessions from local session map." );
            for( final Session session : sessions.values() ) {
                swapOut( (StandardSession) session );
            }
        }

        _msm.shutdown();

        super.stopInternal();
    }

    private void swapOut( final StandardSession session ) {
        // implementation like the one in PersistentManagerBase.swapOut
        if (!session.isValid()) {
            return;
        }
        session.passivate();
        removeInternal(session, true);
        session.recycle();
    }

    @Override
    public void backgroundProcess() {
        _msm.updateExpirationInMemcached();
        super.backgroundProcess();
    }

    public void setSessionBackupAsync( final boolean sessionBackupAsync ) {
        _msm.setSessionBackupAsync( sessionBackupAsync );
    }

    public boolean isSessionBackupAsync() {
        return _msm.isSessionBackupAsync();
    }

    public void setSessionBackupTimeout( final int sessionBackupTimeout ) {
        _msm.setSessionBackupTimeout( sessionBackupTimeout );
    }

    public long getSessionBackupTimeout() {
        return _msm.getSessionBackupTimeout();
    }

    public long getMsmStatNumBackupFailures() {
        return _msm.getStatistics().getRequestsWithBackupFailure();
    }

    public long getMsmStatNumTomcatFailover() {
        return _msm.getStatistics().getRequestsWithTomcatFailover();
    }

    public long getMsmStatNumMemcachedFailover() {
        return _msm.getStatistics().getRequestsWithMemcachedFailover();
    }

    public long getMsmStatNumRequestsWithoutSession() {
        return _msm.getStatistics().getRequestsWithoutSession();
    }

    public long getMsmStatNumNoSessionAccess() {
        return _msm.getStatistics().getRequestsWithoutSessionAccess();
    }

    public long getMsmStatNumNoAttributesAccess() {
        return _msm.getStatistics().getRequestsWithoutAttributesAccess();
    }

    public long getMsmStatNumNoSessionModification() {
        return _msm.getStatistics().getRequestsWithoutSessionModification();
    }

    public long getMsmStatNumRequestsWithSession() {
        return _msm.getStatistics().getRequestsWithSession();
    }

    public long getMsmStatNumNonStickySessionsPingFailed() {
        return _msm.getStatistics().getNonStickySessionsPingFailed();
    }
    public long getMsmStatNumNonStickySessionsReadOnlyRequest() {
        return _msm.getStatistics().getNonStickySessionsReadOnlyRequest();
    }

    public String[] getMsmStatAttributesSerializationInfo() {
        return _msm.getStatistics().getProbe( ATTRIBUTES_SERIALIZATION ).getInfo();
    }

    public String[] getMsmStatEffectiveBackupInfo() {
        return _msm.getStatistics().getProbe( EFFECTIVE_BACKUP ).getInfo();
    }

    public String[] getMsmStatBackupInfo() {
        return _msm.getStatistics().getProbe( BACKUP ).getInfo();
    }

    public String[] getMsmStatSessionsLoadedFromMemcachedInfo() {
        return _msm.getStatistics().getProbe( LOAD_FROM_MEMCACHED ).getInfo();
    }

    public String[] getMsmStatSessionsDeletedFromMemcachedInfo() {
        return _msm.getStatistics().getProbe( DELETE_FROM_MEMCACHED ).getInfo();
    }

    public String[] getMsmStatSessionDeserializationInfo() {
        return _msm.getStatistics().getProbe( SESSION_DESERIALIZATION ).getInfo();
    }

    public String[] getMsmStatCachedDataSizeInfo() {
        return _msm.getStatistics().getProbe( CACHED_DATA_SIZE ).getInfo();
    }

    public String[] getMsmStatMemcachedUpdateInfo() {
        return _msm.getStatistics().getProbe( MEMCACHED_UPDATE ).getInfo();
    }

    public String[] getMsmStatNonStickyAcquireLockInfo() {
        return _msm.getStatistics().getProbe( ACQUIRE_LOCK ).getInfo();
    }

    public String[] getMsmStatNonStickyAcquireLockFailureInfo() {
        return _msm.getStatistics().getProbe( ACQUIRE_LOCK_FAILURE ).getInfo();
    }

    public String[] getMsmStatNonStickyReleaseLockInfo() {
        return _msm.getStatistics().getProbe( RELEASE_LOCK ).getInfo();
    }

    public String[] getMsmStatNonStickyOnBackupWithoutLoadedSessionInfo() {
        return _msm.getStatistics().getProbe( NON_STICKY_ON_BACKUP_WITHOUT_LOADED_SESSION ).getInfo();
    }

    public String[] getMsmStatNonStickyAfterBackupInfo() {
        return _msm.getStatistics().getProbe( NON_STICKY_AFTER_BACKUP ).getInfo();
    }

    public String[] getMsmStatNonStickyAfterLoadFromMemcachedInfo() {
        return _msm.getStatistics().getProbe( NON_STICKY_AFTER_LOAD_FROM_MEMCACHED ).getInfo();
    }

    public String[] getMsmStatNonStickyAfterDeleteFromMemcachedInfo() {
        return _msm.getStatistics().getProbe( NON_STICKY_AFTER_DELETE_FROM_MEMCACHED ).getInfo();
    }

    @Override
    public String getSessionCookieName() {
        return SessionConfig.getSessionCookieName(getContext());
    }

    @Override
    public MemcachedBackupSession getSessionInternal( final String sessionId ) {
        return (MemcachedBackupSession) sessions.get( sessionId );
    }

    @Override
    public Map<String, Session> getSessionsInternal() {
        return sessions;
    }

    @Override
    public String getString( final String key ) {
        return sm.getString( key );
    }

    @Override
    public void incrementSessionCounter() {
        sessionCounter++;
    }

    @Override
    public void incrementRejectedSessions() {
        rejectedSessions++;
    }

    @Override
    public boolean isInitialized() {
        return getState() == LifecycleState.INITIALIZED || getState() == LifecycleState.STARTED;
    }

    @Override
    public String getString( final String key, final Object ... args ) {
        return sm.getString( key, args );
    }

    @Override
    public ClassLoader getContainerClassLoader() {
        return getContext().getLoader().getClassLoader();
    }

    @Override
    public void writePrincipal(  Principal principal,  ObjectOutputStream oos) throws IOException {
        oos.writeObject(principal);
    }

    @Override
    public Principal readPrincipal( final ObjectInputStream ois ) throws ClassNotFoundException, IOException {
        return (Principal) ois.readObject();
    }

    @Override
    public boolean contextHasFormBasedSecurityConstraint(){
        if(_contextHasFormBasedSecurityConstraint != null) {
            return _contextHasFormBasedSecurityConstraint.booleanValue();
        }
        final SecurityConstraint[] constraints = getContext().findConstraints();
        final LoginConfig loginConfig = getContext().getLoginConfig();
        _contextHasFormBasedSecurityConstraint = constraints != null && constraints.length > 0
                && loginConfig != null && HttpServletRequest.FORM_AUTH.equals( loginConfig.getAuthMethod() );
        return _contextHasFormBasedSecurityConstraint;
    }

    @Override
    public MemcachedSessionService getMemcachedSessionService() {
        return _msm;
    }

    @Override
    public String[] getSetCookieHeaders(final Response response) {
        final Collection<String> result = response.getHeaders("Set-Cookie");
        return result.toArray(new String[result.size()]);
    }

}
