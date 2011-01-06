/*
 * eID Identity Provider Project.
 * Copyright (C) 2010 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

package be.fedict.eid.idp.admin.webapp;

import javax.ejb.Local;
import javax.faces.model.SelectItem;
import java.util.List;

@Local
public interface Config {

    /*
     * Accessors.
     */
    String getXkmsUrl();

    void setXkmsUrl(String xkmsUrl);

    String getXkmsAuthTrustDomain();

    void setXkmsAuthTrustDomain(String xkmsAuthTrustDomain);

    String getXkmsIdentTrustDomain();

    void setXkmsIdentTrustDomain(String xkmsIdentTrustDomain);

    String getHmacSecret();

    void setHmacSecret(String hmacSecret);

    Boolean getHttpProxy();

    void setHttpProxy(Boolean httpProxy);

    String getHttpProxyHost();

    void setHttpProxyHost(String httpProxyHost);

    Integer getHttpProxyPort();

    void setHttpProxyPort(Integer httpProxyPort);

    String getSelectedTab();

    void setSelectedTab(String selectedTab);

    /*
    * Factories
    */
    List<SelectItem> keyStoreTypeFactory();

    /*
    * Actions.
    */
    String saveXkms();

    String savePseudonym();

    String saveNetwork();

    /*
     * Lifecycle.
     */
    void destroy();

    void postConstruct();
}
