package com.campusoverflow.reputation.interfaces;

import com.campusoverflow.shared.security.Actor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 测试替身：模拟 bootstrap 的 ActorArgumentResolver，把指定登录主体注入控制器参数。 */
public final class FixedActorResolver implements HandlerMethodArgumentResolver {

    private final Actor actor;

    public FixedActorResolver(Actor actor) {
        this.actor = actor;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return actor;
    }
}
