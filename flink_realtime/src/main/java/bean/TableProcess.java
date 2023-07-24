package bean;


import lombok.Data;

/**
 * @author Akang
 * @create 2023-07-06 15:43
 */
@Data
public class TableProcess {
    public String source_table;
    public String operate_type;
    public String sink_type;
    public String sink_table;
    public String sink_columns;
    public String sink_pk;
    public String sink_extend;

}
