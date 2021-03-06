package ch.bergturbenthal.wisp.manager.service.http;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityManagerFactory;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransactionFilterBean implements Filter {
	private static final Set<String> TRANSACTIONAL_PATH = new HashSet<String>(Arrays.asList("/UIDL/", "/"));
	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	public void destroy() {
	}

	@Override
	@Transactional
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
		final String pathInfo = ((HttpServletRequest) request).getPathInfo();
		if (TRANSACTIONAL_PATH.contains(pathInfo)) {
			try {
				new TransactionTemplate(transactionManager).execute(new TransactionCallback<Void>() {

					@Override
					public Void doInTransaction(final TransactionStatus status) {
						try {
							chain.doFilter(request, response);
						} catch (IOException | ServletException e) {
							throw new RuntimeException("exception on server", e);
						}
						return null;
					}
				});
			} catch (final RuntimeException e) {
				final Throwable cause = e.getCause();
				if (cause instanceof IOException) {
					throw (IOException) cause;
				}
				if (cause instanceof ServletException) {
					throw (ServletException) cause;
				}
				throw e;
			}
		} else {
			chain.doFilter(request, response);
		}
	}

	@Override
	public void init(final FilterConfig filterConfig) throws ServletException {

	}

}
