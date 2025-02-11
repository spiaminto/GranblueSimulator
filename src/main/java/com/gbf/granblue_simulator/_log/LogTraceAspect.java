package com.gbf.granblue_simulator._log;


import com.gbf.granblue_simulator._log.trace.LogTrace;
import com.gbf.granblue_simulator._log.trace.TraceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Aspect @Component
public class LogTraceAspect {

    private final LogTrace logTrace;

    // Pointcut 표현식 분리
    @Pointcut("execution(* com.gbf.granblue_simulator.controller..*(..))")
    public void allController() {};

    @Pointcut("!execution(* com.gbf.granblue_simulator.controller..awsHealthCheck(..))")
    public void ignoreHealthCheck() {};

    @Pointcut("execution(* com.gbf.granblue_simulator.service..*(..))")
    public void allService() {};

    @Pointcut("execution(* com.gbf.granblue_simulator.repository..*(..))")
    public void allRepository() {};

    @Pointcut("execution(* com.gbf.granblue_simulator.logic..*(..))")
    public void allLogic() {};

    @Around("(allLogic() && ignoreHealthCheck())")
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        TraceStatus status = null;
        Object[] params = null;
        try {
            String message = joinPoint.getSignature().toShortString();
            params = joinPoint.getArgs();

            status = logTrace.begin(message);

            Object result = joinPoint.proceed();

            logTrace.end(status);
            return result;
        } catch (Exception e) {
            logTrace.exception(status, e, params);
            throw e; //예외를 처리하진 않음
        }
    }

}
