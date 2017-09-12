/*
 * Copyright 2009-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kerb4j.server.spring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.Assert;

/**
 * <p>Authentication Provider which validates Kerberos Service Tickets
 * or SPNEGO Tokens (which includes Kerberos Service Tickets).</p>
 *
 * <p>It needs a <code>KerberosTicketValidator</code>, which contains the
 * code to validate the ticket, as this code is different between
 * SUN and IBM JRE.<br>
 * It also needs an <code>UserDetailsService</code> to load the user properties
 * and the <code>GrantedAuthorities</code>, as we only get back the username
 * from Kerbeos</p>
 *
 * You can see an example configuration in <code>SpnegoAuthenticationProcessingFilter</code>.
 *
 * @author Mike Wiesner
 * @author Jeremy Stone
 * @since 1.0
 * @see KerberosTicketValidator
 * @see UserDetailsService
 */
public class SpnegoAuthenticationProvider implements
		AuthenticationProvider, InitializingBean {

	private static final Log LOG = LogFactory.getLog(SpnegoAuthenticationProvider.class);

	private KerberosTicketValidator ticketValidator;
	private UserDetailsService userDetailsService;
    private AuthenticationUserDetailsService<SpnegoAuthenticationToken> extractGroupsUserDetailsService =
            new ExtractGroupsUserDetailsService();
	private UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();

	@Override
	public SpnegoAuthenticationToken authenticate(Authentication authentication) {

	    SpnegoRequestToken auth = (SpnegoRequestToken) authentication;
		byte[] token = auth.getToken();

		LOG.debug("Try to validate Kerberos Token");
		SpnegoAuthenticationToken ticketValidation = this.ticketValidator.validateTicket(token);
		LOG.debug("Successfully validated " + ticketValidation.username());

        // Extract roles from PAC
        UserDetails userDetails = null != extractGroupsUserDetailsService ? extractGroupsUserDetailsService.loadUserDetails(ticketValidation) : null;
		userDetails = null == userDetails ? userDetailsService.loadUserByUsername(ticketValidation.username()) : userDetails;

		userDetailsChecker.check(userDetails);

		additionalAuthenticationChecks(userDetails, auth);

		SpnegoAuthenticationToken responseAuth = new SpnegoAuthenticationToken(
				userDetails.getAuthorities(), ticketValidation.getToken(),
				userDetails.getUsername(), ticketValidation.responseToken(),
				ticketValidation.getSubject(), ticketValidation.getKerberosKeys()
		);
		responseAuth.setDetails(authentication.getDetails());

		return  responseAuth;
	}

	@Override
	public boolean supports(Class<?> auth) {
		return SpnegoRequestToken.class.isAssignableFrom(auth);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(this.ticketValidator, "ticketValidator must be specified");
		Assert.notNull(this.userDetailsService, "userDetailsService must be specified");
	}

	/**
	 * The <code>UserDetailsService</code> to use, for loading the user properties
	 * and the <code>GrantedAuthorities</code>.
	 *
	 * @param userDetailsService the new user details service
	 */
	public void setUserDetailsService(UserDetailsService userDetailsService) {
		this.userDetailsService = userDetailsService;
	}

    public void setExtractGroupsUserDetailsService(AuthenticationUserDetailsService<SpnegoAuthenticationToken> extractGroupsUserDetailsService) {
        this.extractGroupsUserDetailsService = extractGroupsUserDetailsService;
    }

    /**
	 * The <code>KerberosTicketValidator</code> to use, for validating
	 * the Kerberos/SPNEGO tickets.
	 *
	 * @param ticketValidator the new ticket validator
	 */
	public void setTicketValidator(KerberosTicketValidator ticketValidator) {
		this.ticketValidator = ticketValidator;
	}

	/**
	 * Allows subclasses to perform any additional checks of a returned <code>UserDetails</code>
	 * for a given authentication request.
	 *
	 * @param userDetails as retrieved from the {@link UserDetailsService}
	 * @param authentication validated {@link SpnegoRequestToken}
	 * @throws AuthenticationException AuthenticationException if the credentials could not be validated (generally a
	 *         <code>BadCredentialsException</code>, an <code>AuthenticationServiceException</code>)
	 */
	protected void additionalAuthenticationChecks(UserDetails userDetails, SpnegoRequestToken authentication)
			throws AuthenticationException {
	}

}
