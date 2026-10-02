package com.cognivault.api;
import java.util.List;
public final class DashboardDtos {private DashboardDtos(){}
 public record Metric(String key,String label,Object value,String change,String severity){}
 public record TrendPoint(String label,long value){}
 public record RiskPoint(String label,long value){}
 public record DashboardResponse(List<Metric> metrics,List<TrendPoint> auditTrend,List<RiskPoint> riskDistribution,List<AuditDtos.AuditResponse> recentEvents){}
}