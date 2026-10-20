package org.example.ticketmanagement.trade;

import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

import javax.sql.DataSource;

/**
 * Real Mapper proxies sharing the service's Spring-managed DataSource connection and transaction.
 */
public final class MapperTestSupport {

    private MapperTestSupport() {
    }

    public static SqlSessionTemplate sessions(DataSource source) {
        try {
            Configuration configuration = new Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMappers("org.example.ticketmanagement.mapper");
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setTransactionFactory(new SpringManagedTransactionFactory());
            factory.setConfiguration(configuration);
            return new SqlSessionTemplate(factory.getObject());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize real MyBatis test mappers", exception);
        }
    }
}
