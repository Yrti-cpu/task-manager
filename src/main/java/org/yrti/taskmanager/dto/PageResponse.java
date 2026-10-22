package org.yrti.taskmanager.dto;
import org.springframework.data.domain.Page;
import java.util.List;
public record PageResponse<T>(List<T> content,int page,int size,long totalElements,int totalPages,boolean first,boolean last){
 public PageResponse(Page<T> p){this(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages(),p.isFirst(),p.isLast());}
}
