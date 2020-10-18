package com.ctrip.platform.dal.dao.datasource.cluster;

import com.ctrip.platform.dal.dao.helper.DalElementFactory;
import com.ctrip.platform.dal.dao.log.ILogger;
import com.ctrip.platform.dal.exceptions.DalException;
import com.ctrip.platform.dal.exceptions.InvalidConnectionException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MajorityHostRouteStrategy implements RouteStrategy{

    private static final ILogger LOGGER = DalElementFactory.DEFAULT.getILogger();
    private static final String CAT_LOG_TYPE = "DAL.pickConnection";
    private static final String HOST_NOT_EXIST = "Router::hostNotExist:%s";
    private static final String NO_HOST_AVAILABLE = "Router::noHostAvailable:%s";

    private ConnectionValidator connectionValidator;
    private HostValidator hostValidator;
    private Set<HostSpec> configuredHosts;
    private ConnectionFactory connFactory;
    private Properties strategyOptions;
    private List<HostSpec> orderHosts;
    private String status; // new --> init --> destroy

    private enum RouteStrategyStatus {
        birth, init, destroy;
    }

    public MajorityHostRouteStrategy() {
        status = RouteStrategyStatus.birth.name();
    }

    private void isInit() throws DalException {
        if (!RouteStrategyStatus.birth.name().equalsIgnoreCase(status))
            throw new DalException("MajorityHostRouteStrategy is not ready, status: " + this.status);
    }

    private void isDestroy () throws DalException {
        if (RouteStrategyStatus.init.name().equalsIgnoreCase(status))
            throw new DalException("MajorityHostRouteStrategy has been init, status: " + this.status);
    }

    @Override
    public Connection pickConnection(RequestContext context) throws SQLException {
        isInit();
        for (int i = 0; i < 9; i++) {
            try {
                String clientZone = context.clientZone();

                HostSpec targetHost = pickHost(connFactory, strategyOptions, clientZone);
                Connection targetConnection = connFactory.getPooledConnectionForHost(targetHost);

                return targetConnection;
            } catch (InvalidConnectionException e) {
                // TODO log something
            } catch (DalException e) {
                LOGGER.error(String.format(NO_HOST_AVAILABLE, " "), e);
                throw e;
            }
        }

        throw new DalException(NO_HOST_AVAILABLE);
    }

    @Override
    public void initialize(Set<HostSpec> configuredHosts, ConnectionFactory connFactory, Properties strategyOptions) throws DalException {
        isDestroy();
        this.status = RouteStrategyStatus.init.name();
        this.configuredHosts = configuredHosts;
        this.connFactory = connFactory;
        this.strategyOptions = strategyOptions;
        buildValidator();
        buildOrderHosts();
    }

    private void buildOrderHosts () {
        // TODO buildOrderHosts
    }

    private void buildValidator() {
        long failOverTime = 0;
        long blackListTimeOut = 0;
        MajorityHostValidator validator = new MajorityHostValidator(configuredHosts, failOverTime, blackListTimeOut);
        this.connectionValidator = validator;
        this.hostValidator = validator;
    }

    @Override
    public ConnectionValidator getConnectionValidator() throws DalException {
        isInit();
        return connectionValidator;
    }

    @Override
    public void destroy() throws DalException {
        isInit();
        this.status = RouteStrategyStatus.destroy.name();
    }

    private HostSpec pickHost(ConnectionFactory factory, Properties options, String clientZone) throws DalException {
        for (HostSpec hostSpec : orderHosts) {
            if (hostValidator.available(factory, hostSpec)) {
                return hostSpec;
            }
        }

        throw new DalException(String.format(NO_HOST_AVAILABLE, orderHosts.toString()));
    }
}
