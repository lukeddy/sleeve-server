package com.quan.windsleeve.core.interceptor;

import com.auth0.jwt.interfaces.Claim;
import com.quan.windsleeve.core.annotation.ScopeLevel;
import com.quan.windsleeve.exception.http.NoAuthorizationException;
import com.quan.windsleeve.util.JwtToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

public class JwtTokenInterceptor extends HandlerInterceptorAdapter {
    /**
     *
     * @param request
     * @param response
     * @param handler 就是当前拦截器，需要拦截的对象
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("进入拦截器");
        //判断当前被访问的api是否需要权限认证
        Optional<ScopeLevel> scopeLevel = getAPIScopeLevel(handler);
        //如果当前api不存在scopeLevel，证明它是公开的api接口
        if(!scopeLevel.isPresent()) {
            return true;
        }
        //从request中获取token
        String token = getTokenFromHeader(request);
        System.out.println("从header中获取到的token: "+token);
        //验证token
        Map<String,Claim> claimMap = JwtToken.getClaimByVerify(token);
        Claim scopeClaim = claimMap.get("scope");
        Integer tokenLevel = scopeClaim.asInt();
        System.out.println("当前token的scope: "+tokenLevel);
        //验证当前token是否有权限访问api
        Boolean isPermission = isTokenPermission(tokenLevel,scopeLevel.get());
        //验证当前token是否过期
        if(isPermission == true) {
           Boolean isTokenExpire = isTokenExpire(claimMap.get("expireTime").asLong());
           if(isTokenExpire == true) {
               return true;
           }
        }
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        super.afterCompletion(request, response, handler, ex);
    }

    /**
     * 判断当前token是否过期
     * @param expireTime
     */
    private Boolean isTokenExpire(Long expireTime) {
        Long currentTime = System.currentTimeMillis();
        //如果当前时间 < 过期时间，那么认为当前token可以继续使用
        if(currentTime <= expireTime) {
            return true;
        }
        throw new NoAuthorizationException(10003);
    }

    /**
     * 验证当前token的level是否 > 当前api默认的level权限，如果大于，可以访问该api，小于则不可以
     * @param tokenLevel
     * @param scopeLevel
     * @return
     */
    private Boolean isTokenPermission(int tokenLevel, ScopeLevel scopeLevel) {
        int apiScopeLevel = scopeLevel.value();
        System.out.println("当前api接口的访问权限： "+apiScopeLevel);
        //如果token令牌中的level < api中的level，证明用户没有权限访问当前的api接口
        if(tokenLevel < apiScopeLevel) {
            throw new NoAuthorizationException(10002);
        }
        return true;
    }

    /**
     * 从请求头中获取token
     * @param request
     */
    private String getTokenFromHeader(HttpServletRequest request) {
        String jwtToken = request.getHeader("Authorization");
        if(jwtToken == null) {
            throw new NoAuthorizationException(10001);
        }
        String[] tokenArr = jwtToken.split(" ");
        String token = tokenArr[1];
        if(token == null || token == "") {
            throw new NoAuthorizationException(10001);
        }
        return token;
    }

    /**
     * 获取当前api方法的scopeLevel
     * @param handler
     * @return Optional<ScopeLevel>
     */
    private Optional<ScopeLevel> getAPIScopeLevel(Object handler) {
        /**
         * HandlerMethod类是Spring MVC的类，可以通过该类获取接口方法中的 注解，参数等
         */
        if(handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            ScopeLevel scopeLevel = handlerMethod.getMethod().getAnnotation(ScopeLevel.class);
            if(scopeLevel == null) return Optional.empty();
            return Optional.of(scopeLevel);
        }
        return Optional.empty();
    }
}
